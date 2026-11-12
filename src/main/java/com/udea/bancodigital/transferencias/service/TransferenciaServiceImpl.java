package com.udea.bancodigital.transferencias.service;

import com.udea.bancodigital.cuentas.api.CuentaApi;
import com.udea.bancodigital.shared.exception.CodigosError;
import com.udea.bancodigital.shared.exception.NegocioException;
import com.udea.bancodigital.transferencias.dto.TransferenciaRequest;
import com.udea.bancodigital.transferencias.dto.TransferenciaResponse;
import com.udea.bancodigital.transferencias.entity.TipoTransaccion;
import com.udea.bancodigital.transferencias.entity.Transaccion;
import com.udea.bancodigital.transferencias.repository.TipoTransaccionRepository;
import com.udea.bancodigital.transferencias.repository.TransaccionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TransferenciaServiceImpl implements TransferenciaService {

    private static final String ESTADO_EXITOSA = "EXITOSA";

    private final TransaccionRepository transaccionRepository;
    private final TipoTransaccionRepository tipoTransaccionRepository;

    // Dependencia al modulo de cuentas (Monolito Modular)
    private final CuentaApi cuentaApi;

    @Override
    @Transactional // Una sola transaccion para debitar, acreditar y registrar
    public TransferenciaResponse realizarTransferencia(Long clienteId, TransferenciaRequest request) {

        if (clienteId == null) {
            throw new NegocioException(CodigosError.UNAUTHORIZED, "Usuario no autenticado", HttpStatus.UNAUTHORIZED);
        }

        if (request.getNumeroCuentaOrigen().equals(request.getNumeroCuentaDestino())) {
            throw new NegocioException(
                    "SAME_ACCOUNT",
                    "La cuenta de origen y destino no pueden ser la misma",
                    HttpStatus.BAD_REQUEST
            );
        }

        // 1. Bloquear las dos cuentas antes de leer sus saldos. Sin el bloqueo, dos
        // transferencias simultaneas desde la misma cuenta leen el mismo saldo y
        // las dos pasan la validacion de fondos (doble gasto). Se bloquean siempre
        // en el mismo orden (por numero de cuenta): si A->B y B->A llegaran a la
        // vez y cada una bloqueara primero su origen, se esperarian para siempre.
        String numeroOrigen = request.getNumeroCuentaOrigen();
        String numeroDestino = request.getNumeroCuentaDestino();
        Optional<CuentaApi.CuentaInfo> origenBloqueado;
        Optional<CuentaApi.CuentaInfo> destinoBloqueado;
        if (numeroOrigen.compareTo(numeroDestino) < 0) {
            origenBloqueado = cuentaApi.bloquearPorNumeroCuenta(numeroOrigen);
            destinoBloqueado = cuentaApi.bloquearPorNumeroCuenta(numeroDestino);
        } else {
            destinoBloqueado = cuentaApi.bloquearPorNumeroCuenta(numeroDestino);
            origenBloqueado = cuentaApi.bloquearPorNumeroCuenta(numeroOrigen);
        }

        // DEF-HU12-07: cada cuenta inexistente se reporta con su propio codigo.
        // El orden importa: primero se valida todo el origen (existe, es del
        // cliente, esta activo) y solo despues el destino, para no revelar nada
        // del destino a quien usa una cuenta de origen ajena.
        CuentaApi.CuentaInfo cuentaOrigen = origenBloqueado.orElseThrow(() -> new NegocioException(
                "SOURCE_ACCOUNT_NOT_FOUND",
                "La cuenta de origen " + numeroOrigen + " no existe",
                HttpStatus.NOT_FOUND
        ));

        // Verificar que la cuenta origen pertenezca al cliente autenticado
        if (!cuentaOrigen.clienteId().equals(clienteId)) {
            throw new NegocioException(
                    "FORBIDDEN",
                    "La cuenta de origen no pertenece al usuario autenticado",
                    HttpStatus.FORBIDDEN
            );
        }

        // 2. Validar estados (Ambas deben estar ACTIVAS)
        if (!"ACTIVA".equalsIgnoreCase(cuentaOrigen.estadoNombre())) {
            throw new NegocioException(
                    "ACCOUNT_INACTIVE",
                    "La cuenta de origen debe estar ACTIVA",
                    HttpStatus.CONFLICT
            );
        }

        CuentaApi.CuentaInfo cuentaDestino = destinoBloqueado.orElseThrow(() -> new NegocioException(
                "DESTINATION_ACCOUNT_NOT_FOUND",
                "La cuenta de destino " + numeroDestino + " no existe",
                HttpStatus.NOT_FOUND
        ));

        if (!"ACTIVA".equalsIgnoreCase(cuentaDestino.estadoNombre())) {
            throw new NegocioException(
                    "DESTINATION_ACCOUNT_INACTIVE",
                    "La cuenta de destino debe estar ACTIVA",
                    HttpStatus.CONFLICT
            );
        }

        // 3. Validar fondos suficientes
        if (cuentaOrigen.saldoDisponible().compareTo(request.getMonto()) < 0) {
            throw new NegocioException(
                    "INSUFFICIENT_FUNDS",
                    "Saldo insuficiente para realizar la transferencia",
                    HttpStatus.BAD_REQUEST
            );
        }

        // 4. Actualizar saldos a traves del API de Cuentas (las filas siguen bloqueadas)
        BigDecimal nuevoSaldoOrigen = cuentaOrigen.saldoDisponible().subtract(request.getMonto());
        BigDecimal nuevoSaldoDestino = cuentaDestino.saldoDisponible().add(request.getMonto());

        cuentaApi.actualizarSaldo(cuentaOrigen.id(), nuevoSaldoOrigen);
        cuentaApi.actualizarSaldo(cuentaDestino.id(), nuevoSaldoDestino);

        // 5. Registrar la transaccion
        TipoTransaccion tipoTransferencia = tipoTransaccionRepository.findByNombreIgnoreCase("TRANSFERENCIA")
                .orElseThrow(() -> new NegocioException(
                        "INTERNAL_ERROR",
                        "Tipo de transacción 'TRANSFERENCIA' no configurado",
                        HttpStatus.INTERNAL_SERVER_ERROR
                ));

        Transaccion transaccion = Transaccion.builder()
                .cuentaOrigenId(cuentaOrigen.id())
                .cuentaDestinoId(cuentaDestino.id())
                .tipoTransaccion(tipoTransferencia)
                .monto(request.getMonto())
                .estado(ESTADO_EXITOSA)
                .descripcion(request.getDescripcion())
                .fechaHora(OffsetDateTime.now())
                .build();

        Transaccion guardada = transaccionRepository.save(transaccion);

        // 6. Retornar respuesta exitosa
        return TransferenciaResponse.builder()
                .transaccionId(guardada.getId())
                .numeroCuentaOrigen(cuentaOrigen.numeroCuenta())
                .numeroCuentaDestino(cuentaDestino.numeroCuenta())
                .monto(guardada.getMonto())
                .estado(guardada.getEstado())
                .fechaHora(guardada.getFechaHora())
                .build();
    }

}


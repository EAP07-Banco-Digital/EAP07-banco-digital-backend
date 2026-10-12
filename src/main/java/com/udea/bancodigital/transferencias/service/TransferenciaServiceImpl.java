package com.udea.bancodigital.transferencias.service;

import com.udea.bancodigital.cuentas.api.CuentaApi;
import com.udea.bancodigital.shared.exception.NegocioException;
import com.udea.bancodigital.transferencias.api.TransaccionApi;
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

@Service
@RequiredArgsConstructor
public class TransferenciaServiceImpl implements TransferenciaService, TransaccionApi {

    private final TransaccionRepository transaccionRepository;
    private final TipoTransaccionRepository tipoTransaccionRepository;

    // Dependencia al modulo de cuentas (Monolito Modular)
    private final CuentaApi cuentaApi;

    @Override
    @Transactional // Una sola transaccion para debitar, acreditar y registrar
    public TransferenciaResponse realizarTransferencia(Long clienteId, TransferenciaRequest request) {

        if (clienteId == null) {
            throw new NegocioException("UNAUTHORIZED", "Usuario no autenticado", HttpStatus.UNAUTHORIZED);
        }

        if (request.getNumeroCuentaOrigen().equals(request.getNumeroCuentaDestino())) {
            throw new NegocioException(
                    "SAME_ACCOUNT",
                    "La cuenta de origen y destino no pueden ser la misma",
                    HttpStatus.BAD_REQUEST
            );
        }

        // 1. Obtener informacion de las cuentas desde el modulo de Cuentas
        CuentaApi.CuentaInfo cuentaOrigen = cuentaApi.obtenerInfoPorNumeroCuenta(request.getNumeroCuentaOrigen());

        // Verificar que la cuenta origen pertenezca al cliente autenticado
        if (!cuentaOrigen.clienteId().equals(clienteId)) {
            throw new NegocioException(
                    "FORBIDDEN",
                    "La cuenta de origen no pertenece al usuario autenticado",
                    HttpStatus.FORBIDDEN
            );
        }

        CuentaApi.CuentaInfo cuentaDestino = cuentaApi.obtenerInfoPorNumeroCuenta(request.getNumeroCuentaDestino());

        // 2. Validar estados (Ambas deben estar ACTIVAS)
        if (!"ACTIVA".equalsIgnoreCase(cuentaOrigen.estadoNombre())) {
            throw new NegocioException(
                    "ACCOUNT_INACTIVE",
                    "La cuenta de origen debe estar ACTIVA",
                    HttpStatus.CONFLICT
            );
        }

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

        // 4. Actualizar saldos a traves del API de Cuentas
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
                .estado("EXITOSA")
                .fechaHora(guardada.getFechaHora())
                .build();
    }

    @Override
    @Transactional
    public TransaccionInfo registrarTransaccion(
            Long cuentaOrigenId,
            Long cuentaDestinoId,
            String tipoTransaccionNombre,
            BigDecimal monto,
            String descripcion
    ) {
        TipoTransaccion tipo = tipoTransaccionRepository.findByNombreIgnoreCase(tipoTransaccionNombre)
                .orElseThrow(() -> new NegocioException(
                        "INTERNAL_ERROR",
                        "Tipo de transacción '" + tipoTransaccionNombre + "' no configurado",
                        HttpStatus.INTERNAL_SERVER_ERROR
                ));

        Transaccion transaccion = Transaccion.builder()
                .cuentaOrigenId(cuentaOrigenId)
                .cuentaDestinoId(cuentaDestinoId)
                .tipoTransaccion(tipo)
                .monto(monto)
                .descripcion(descripcion)
                .fechaHora(OffsetDateTime.now())
                .build();

        Transaccion guardada = transaccionRepository.save(transaccion);
        return new TransaccionInfo(guardada.getId(), guardada.getFechaHora());
    }
}

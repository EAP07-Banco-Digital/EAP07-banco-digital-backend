package com.udea.bancodigital.cuentas.service;

import com.udea.bancodigital.cuentas.api.CuentaApi;
import com.udea.bancodigital.cuentas.dto.AperturaCuentaRequest;
import com.udea.bancodigital.cuentas.dto.CuentaResponse;
import com.udea.bancodigital.cuentas.dto.OperacionCuentaRequest;
import com.udea.bancodigital.cuentas.dto.OperacionCuentaResponse;
import com.udea.bancodigital.cuentas.entity.Cuenta;
import com.udea.bancodigital.shared.entity.Estado;
import com.udea.bancodigital.cuentas.entity.TipoCuenta;
import com.udea.bancodigital.cuentas.repository.CuentaRepository;
import com.udea.bancodigital.shared.repository.EstadoRepository;
import com.udea.bancodigital.cuentas.repository.TipoCuentaRepository;
import com.udea.bancodigital.shared.exception.NegocioException;
import com.udea.bancodigital.transferencias.api.TransaccionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class CuentaServiceImpl implements CuentaService, CuentaApi {

    private final CuentaRepository cuentaRepository;
    private final TipoCuentaRepository tipoCuentaRepository;
    private final EstadoRepository estadoRepository;
    private final TransaccionApi transaccionApi;

    @Override
    @Transactional
    public CuentaResponse solicitarAperturaCuenta(Long clienteId, AperturaCuentaRequest request) {
        if (clienteId == null) {
            throw new NegocioException("UNAUTHORIZED", "Usuario no autenticado", HttpStatus.UNAUTHORIZED);
        }

        String tipoNombre = request.getTipoCuenta().toUpperCase().trim();

        // 1. Validar tipo de cuenta válido (AHORROS o CORRIENTE)
        TipoCuenta tipoCuenta = tipoCuentaRepository.findByNombreIgnoreCase(tipoNombre)
                .orElseThrow(() -> new NegocioException(
                        "INVALID_ACCOUNT_TYPE",
                        "El tipo de cuenta especificado no es válido: " + request.getTipoCuenta(),
                        HttpStatus.BAD_REQUEST
                ));

        // 2. Validar si ya posee una cuenta ACTIVA de ese tipo (HU5 - Criterio 3)
        boolean yaTieneCuentaActiva = cuentaRepository.existsCuentaActivaPorClienteYTipo(clienteId, tipoNombre);
        if (yaTieneCuentaActiva) {
            throw new NegocioException(
                    "ACCOUNT_TYPE_ALREADY_EXISTS",
                    "El cliente ya posee una cuenta activa del tipo " + tipoNombre,
                    HttpStatus.CONFLICT
            );
        }

        // 3. Obtener el Estado "ACTIVA"
        Estado estadoActiva = estadoRepository.findByNombreIgnoreCase("ACTIVA")
                .orElseThrow(() -> new NegocioException(
                        "INTERNAL_ERROR",
                        "Estado 'ACTIVA' no configurado en el sistema",
                        HttpStatus.INTERNAL_SERVER_ERROR
                ));

        // 4. Generar número único de cuenta
        String numeroCuentaUnico = generarNumeroCuentaUnico();

        // 5. Construir y guardar la cuenta con saldo 0
        Cuenta nuevaCuenta = Cuenta.builder()
                .clienteId(clienteId)
                .numeroCuenta(numeroCuentaUnico)
                .tipoCuenta(tipoCuenta)
                .saldoDisponible(BigDecimal.ZERO)
                .estado(estadoActiva)
                .fechaApertura(OffsetDateTime.now())
                .build();

        Cuenta cuentaGuardada = cuentaRepository.save(nuevaCuenta);

        return mapToResponse(cuentaGuardada);
    }

    @Override
    @Transactional(readOnly = true)
    public CuentaResponse consultarSaldo(Long clienteId, String numeroCuenta) {
        if (clienteId == null) {
            throw new NegocioException("UNAUTHORIZED", "Usuario no autenticado", HttpStatus.UNAUTHORIZED);
        }

        // 1. Buscar cuenta o retornar 404
        Cuenta cuenta = cuentaRepository.findByNumeroCuenta(numeroCuenta)
                .orElseThrow(() -> new NegocioException(
                        "ACCOUNT_NOT_FOUND",
                        "La cuenta consultada no existe",
                        HttpStatus.NOT_FOUND
                ));

        // 2. Verificar titularidad (HU6 - Criterio 2)
        if (!cuenta.getClienteId().equals(clienteId)) {
            throw new NegocioException(
                    "FORBIDDEN",
                    "No tiene permisos para consultar el saldo de esta cuenta",
                    HttpStatus.FORBIDDEN
            );
        }

        // HU6 - Criterio 4: Si está BLOQUEADA, la consulta retorna saldo + estado con HTTP 200
        return mapToResponse(cuenta);
    }

    @Override
    @Transactional
    public OperacionCuentaResponse registrarCredito(Long clienteId, String numeroCuenta, OperacionCuentaRequest request) {
        if (clienteId == null) {
            throw new NegocioException("UNAUTHORIZED", "Usuario no autenticado", HttpStatus.UNAUTHORIZED);
        }

        if (request == null || request.getMonto() == null || request.getMonto().compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException(
                    "INVALID_AMOUNT",
                    "El monto del crédito debe ser mayor a cero",
                    HttpStatus.BAD_REQUEST
            );
        }

        Cuenta cuenta = cuentaRepository.findByNumeroCuenta(numeroCuenta)
                .orElseThrow(() -> new NegocioException(
                        "ACCOUNT_NOT_FOUND",
                        "La cuenta consultada no existe",
                        HttpStatus.NOT_FOUND
                ));

        if (!cuenta.getClienteId().equals(clienteId)) {
            throw new NegocioException(
                    "FORBIDDEN",
                    "No tiene permisos para operar sobre esta cuenta",
                    HttpStatus.FORBIDDEN
            );
        }

        if (!"ACTIVA".equalsIgnoreCase(cuenta.getEstado().getNombre())) {
            throw new NegocioException(
                    "ACCOUNT_NOT_ACTIVE",
                    "La cuenta se encuentra bloqueada o inactiva",
                    HttpStatus.CONFLICT
            );
        }

        BigDecimal nuevoSaldo = cuenta.getSaldoDisponible().add(request.getMonto());
        cuenta.setSaldoDisponible(nuevoSaldo);
        cuentaRepository.save(cuenta);

        TransaccionApi.TransaccionInfo transaccionInfo = transaccionApi.registrarTransaccion(
                cuenta.getId(),
                null,
                "CREDITO",
                request.getMonto(),
                "Depósito a cuenta"
        );

        return OperacionCuentaResponse.builder()
                .transaccionId(transaccionInfo.transaccionId())
                .numeroCuenta(cuenta.getNumeroCuenta())
                .monto(request.getMonto())
                .saldoNuevo(nuevoSaldo)
                .estado("EXITOSA")
                .fechaHora(transaccionInfo.fechaHora())
                .build();
    }

    @Override
    @Transactional
    public OperacionCuentaResponse registrarDebito(Long clienteId, String numeroCuenta, OperacionCuentaRequest request) {
        if (clienteId == null) {
            throw new NegocioException("UNAUTHORIZED", "Usuario no autenticado", HttpStatus.UNAUTHORIZED);
        }

        if (request == null || request.getMonto() == null || request.getMonto().compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException(
                    "INVALID_AMOUNT",
                    "El monto del débito debe ser mayor a cero",
                    HttpStatus.BAD_REQUEST
            );
        }

        Cuenta cuenta = cuentaRepository.findByNumeroCuenta(numeroCuenta)
                .orElseThrow(() -> new NegocioException(
                        "ACCOUNT_NOT_FOUND",
                        "La cuenta consultada no existe",
                        HttpStatus.NOT_FOUND
                ));

        if (!cuenta.getClienteId().equals(clienteId)) {
            throw new NegocioException(
                    "FORBIDDEN",
                    "No tiene permisos para operar sobre esta cuenta",
                    HttpStatus.FORBIDDEN
            );
        }

        if (!"ACTIVA".equalsIgnoreCase(cuenta.getEstado().getNombre())) {
            throw new NegocioException(
                    "ACCOUNT_NOT_ACTIVE",
                    "La cuenta se encuentra bloqueada o inactiva",
                    HttpStatus.CONFLICT
            );
        }

        if (cuenta.getSaldoDisponible().compareTo(request.getMonto()) < 0) {
            throw new NegocioException(
                    "INSUFFICIENT_FUNDS",
                    "Saldo insuficiente para realizar el débito",
                    HttpStatus.BAD_REQUEST
            );
        }

        BigDecimal nuevoSaldo = cuenta.getSaldoDisponible().subtract(request.getMonto());
        cuenta.setSaldoDisponible(nuevoSaldo);
        cuentaRepository.save(cuenta);

        TransaccionApi.TransaccionInfo transaccionInfo = transaccionApi.registrarTransaccion(
                cuenta.getId(),
                null,
                "DEBITO",
                request.getMonto(),
                "Retiro de cuenta"
        );

        return OperacionCuentaResponse.builder()
                .transaccionId(transaccionInfo.transaccionId())
                .numeroCuenta(cuenta.getNumeroCuenta())
                .monto(request.getMonto())
                .saldoNuevo(nuevoSaldo)
                .estado("EXITOSA")
                .fechaHora(transaccionInfo.fechaHora())
                .build();
    }

    // --- Métodos de la CuentaApi (para comunicación entre módulos) ---

    @Override
    @Transactional(readOnly = true)
    public CuentaInfo obtenerInfoPorNumeroCuenta(String numeroCuenta) {
        Cuenta cuenta = cuentaRepository.findByNumeroCuenta(numeroCuenta)
                .orElseThrow(() -> new NegocioException(
                        "DESTINATION_ACCOUNT_NOT_FOUND",
                        "La cuenta " + numeroCuenta + " no existe",
                        HttpStatus.NOT_FOUND
                ));

        return new CuentaInfo(
                cuenta.getId(),
                cuenta.getClienteId(),
                cuenta.getNumeroCuenta(),
                cuenta.getSaldoDisponible(),
                cuenta.getEstado().getNombre()
        );
    }

    @Override
    @Transactional
    public void actualizarSaldo(Long cuentaId, BigDecimal nuevoSaldo) {
        Cuenta cuenta = cuentaRepository.findById(cuentaId)
                .orElseThrow(() -> new NegocioException(
                        "INTERNAL_ERROR",
                        "Cuenta " + cuentaId + " no encontrada al actualizar saldo",
                        HttpStatus.INTERNAL_SERVER_ERROR
                ));
        cuenta.setSaldoDisponible(nuevoSaldo);
        cuentaRepository.save(cuenta);
    }

    private String generarNumeroCuentaUnico() {
        Random random = new Random();
        String numero;
        do {
            long randomNum = 1000000000L + (long) (random.nextDouble() * 9000000000L);
            numero = String.valueOf(randomNum);
        } while (cuentaRepository.existsByNumeroCuenta(numero));
        return numero;
    }

    private CuentaResponse mapToResponse(Cuenta cuenta) {
        return CuentaResponse.builder()
                .id(cuenta.getId())
                .clienteId(cuenta.getClienteId())
                .numeroCuenta(cuenta.getNumeroCuenta())
                .tipoCuenta(cuenta.getTipoCuenta().getNombre())
                .saldoDisponible(cuenta.getSaldoDisponible())
                .estado(cuenta.getEstado().getNombre())
                .fechaApertura(cuenta.getFechaApertura())
                .build();
    }
}

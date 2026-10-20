package com.udea.bancodigital.cuentas.service;

import com.udea.bancodigital.cuentas.dto.AperturaCuentaRequest;
import com.udea.bancodigital.cuentas.dto.CuentaResponse;
import com.udea.bancodigital.cuentas.dto.CuentaResumenResponse;
import com.udea.bancodigital.cuentas.dto.HistorialTransaccionResponse;
import com.udea.bancodigital.cuentas.dto.OperacionCuentaRequest;
import com.udea.bancodigital.cuentas.dto.OperacionCuentaResponse;
import com.udea.bancodigital.cuentas.entity.Cuenta;
import com.udea.bancodigital.shared.entity.Estado;
import com.udea.bancodigital.cuentas.entity.TipoCuenta;
import com.udea.bancodigital.cuentas.repository.CuentaRepository;
import com.udea.bancodigital.shared.repository.EstadoRepository;
import com.udea.bancodigital.cuentas.repository.TipoCuentaRepository;
import com.udea.bancodigital.shared.exception.CodigosError;
import com.udea.bancodigital.shared.exception.NegocioException;
import com.udea.bancodigital.transferencias.api.TransaccionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CuentaServiceImpl implements CuentaService {

    // SecureRandom y no Random: con Random, conocer unos pocos numeros de cuenta
    // emitidos permite predecir los siguientes. Es thread-safe y se reutiliza.
    private static final SecureRandom GENERADOR_NUMERO_CUENTA = new SecureRandom();
    private static final long MENOR_NUMERO_CUENTA = 1_000_000_000L;
    private static final long CANTIDAD_NUMEROS_CUENTA = 9_000_000_000L;

    private final CuentaRepository cuentaRepository;
    private final TipoCuentaRepository tipoCuentaRepository;
    private final EstadoRepository estadoRepository;
    private final TransaccionApi transaccionApi;

    @Override
    @Transactional
    public CuentaResponse solicitarAperturaCuenta(Long clienteId, AperturaCuentaRequest request) {
        if (clienteId == null) {
            throw new NegocioException(CodigosError.UNAUTHORIZED, "Usuario no autenticado", HttpStatus.UNAUTHORIZED);
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
            throw new NegocioException(CodigosError.UNAUTHORIZED, "Usuario no autenticado", HttpStatus.UNAUTHORIZED);
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
    @Transactional(readOnly = true)
    public List<CuentaResumenResponse> listarCuentas(Long clienteId, Long clienteIdSolicitado) {
        if (clienteId == null) {
            throw new NegocioException("UNAUTHORIZED", "Usuario no autenticado", HttpStatus.UNAUTHORIZED);
        }

        // HU11 - Escenario 3: un cliente no puede consultar las cuentas de otro
        if (clienteIdSolicitado != null && !clienteIdSolicitado.equals(clienteId)) {
            throw new NegocioException(
                    "FORBIDDEN",
                    "No tiene permisos para consultar las cuentas de otro cliente",
                    HttpStatus.FORBIDDEN
            );
        }

        // HU11 - Escenarios 1 y 2: lista con cuentas, o lista vacía (siempre HTTP 200)
        return cuentaRepository.findByClienteIdOrderByFechaAperturaDesc(clienteId)
                .stream()
                .map(this::mapToResumen)
                .toList();
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

    @Override
    @Transactional(readOnly = true)
    public Page<HistorialTransaccionResponse> consultarHistorial(
            Long clienteId,
            String numeroCuenta,
            LocalDate desde,
            LocalDate hasta,
            String tipoTransaccion,
            int page,
            int size
    ) {
        if (clienteId == null) {
            throw new NegocioException("UNAUTHORIZED", "Usuario no autenticado", HttpStatus.UNAUTHORIZED);
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
                    "No tiene permisos para consultar el historial de esta cuenta",
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

        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new NegocioException(
                    "INVALID_DATE_RANGE",
                    "La fecha 'desde' no puede ser posterior a la fecha 'hasta'",
                    HttpStatus.BAD_REQUEST
            );
        }

        OffsetDateTime desdeOffset = (desde != null) ? desde.atStartOfDay().atOffset(ZoneOffset.UTC) : null;
        OffsetDateTime hastaOffset = (hasta != null) ? hasta.atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC) : null;

        int pageIndex = Math.max(0, page - 1);
        int pageSize = size > 0 ? size : 10;

        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "fechaHora"));

        Page<TransaccionApi.TransaccionDetalle> transaccionesPage = transaccionApi.consultarHistorial(
                cuenta.getId(),
                desdeOffset,
                hastaOffset,
                tipoTransaccion,
                pageable
        );

        return transaccionesPage.map(t -> {
            String tipoMovimiento;
            if ("CREDITO".equalsIgnoreCase(t.tipoTransaccion())) {
                tipoMovimiento = "ENTRADA";
            } else if ("DEBITO".equalsIgnoreCase(t.tipoTransaccion())) {
                tipoMovimiento = "SALIDA";
            } else if (cuenta.getId().equals(t.cuentaOrigenId())) {
                tipoMovimiento = "SALIDA";
            } else {
                tipoMovimiento = "ENTRADA";
            }

            String numeroOrigen = null;
            if (t.cuentaOrigenId() != null) {
                if (t.cuentaOrigenId().equals(cuenta.getId())) {
                    numeroOrigen = cuenta.getNumeroCuenta();
                } else {
                    numeroOrigen = cuentaRepository.findById(t.cuentaOrigenId())
                            .map(Cuenta::getNumeroCuenta)
                            .orElse(null);
                }
            }

            String numeroDestino = null;
            if (t.cuentaDestinoId() != null) {
                if (t.cuentaDestinoId().equals(cuenta.getId())) {
                    numeroDestino = cuenta.getNumeroCuenta();
                } else {
                    numeroDestino = cuentaRepository.findById(t.cuentaDestinoId())
                            .map(Cuenta::getNumeroCuenta)
                            .orElse(null);
                }
            }

            return HistorialTransaccionResponse.builder()
                    .transaccionId(t.id())
                    .fechaHora(t.fechaHora())
                    .tipoTransaccion(t.tipoTransaccion())
                    .monto(t.monto())
                    .descripcion(t.descripcion())
                    .numeroCuentaOrigen(numeroOrigen)
                    .numeroCuentaDestino(numeroDestino)
                    .tipoMovimiento(tipoMovimiento)
                    .build();
        });
    }


    private String generarNumeroCuentaUnico() {
        String numero;
        do {
            // Siempre 10 digitos: de 1000000000 a 9999999999.
            numero = String.valueOf(MENOR_NUMERO_CUENTA + GENERADOR_NUMERO_CUENTA.nextLong(CANTIDAD_NUMEROS_CUENTA));
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

    private CuentaResumenResponse mapToResumen(Cuenta cuenta) {
        return CuentaResumenResponse.builder()
                .id(cuenta.getId())
                .numeroCuenta(cuenta.getNumeroCuenta())
                .tipoCuenta(cuenta.getTipoCuenta().getNombre())
                .estado(cuenta.getEstado().getNombre())
                .fechaApertura(cuenta.getFechaApertura())
                .build();
    }
}

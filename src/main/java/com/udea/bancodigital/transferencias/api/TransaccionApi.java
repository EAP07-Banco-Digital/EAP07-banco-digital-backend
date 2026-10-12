package com.udea.bancodigital.transferencias.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * API pública del módulo de Transferencias / Transacciones.
 * Permite que otros módulos (ej. Cuentas) interactúen con transacciones
 * sin acoplarse directamente a las entidades JPA internas.
 */
public interface TransaccionApi {

    record TransaccionInfo(
            Long transaccionId,
            OffsetDateTime fechaHora
    ) {}

    record TransaccionDetalle(
            Long id,
            Long cuentaOrigenId,
            Long cuentaDestinoId,
            String tipoTransaccion,
            BigDecimal monto,
            OffsetDateTime fechaHora,
            String descripcion
    ) {}

    TransaccionInfo registrarTransaccion(
            Long cuentaOrigenId,
            Long cuentaDestinoId,
            String tipoTransaccionNombre,
            BigDecimal monto,
            String descripcion
    );

    Page<TransaccionDetalle> consultarHistorial(
            Long cuentaId,
            OffsetDateTime desde,
            OffsetDateTime hasta,
            String tipoTransaccion,
            Pageable pageable
    );
}

package com.udea.bancodigital.transferencias.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * API pública del módulo de Transferencias / Transacciones.
 * Permite que otros módulos (ej. Cuentas) registren transacciones
 * sin acoplarse directamente a las entidades JPA internas.
 */
public interface TransaccionApi {

    record TransaccionInfo(
            Long transaccionId,
            OffsetDateTime fechaHora
    ) {}

    TransaccionInfo registrarTransaccion(
            Long cuentaOrigenId,
            Long cuentaDestinoId,
            String tipoTransaccionNombre,
            BigDecimal monto,
            String descripcion
    );
}

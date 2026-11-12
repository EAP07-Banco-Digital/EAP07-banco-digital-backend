package com.udea.bancodigital.reportes.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * HU20: reporte administrativo de operaciones: resumen agregado y detalle.
 */
@Schema(description = "Reporte administrativo de operaciones (HU20)")
public record ReporteOperacionesResponse(
        OffsetDateTime generadoEn,
        FiltroOperaciones filtros,
        Resumen resumen,
        @Schema(description = "Operaciones en orden cronologico; lista vacia si no hubo")
        List<Operacion> operaciones
) {

    @Schema(description = "Totales del reporte")
    public record Resumen(
            @Schema(example = "3") long totalOperaciones,
            @Schema(example = "185000.00") BigDecimal montoTotal,
            List<TotalPorTipo> porTipo
    ) {
    }

    @Schema(description = "Totales de un tipo de transaccion")
    public record TotalPorTipo(
            @Schema(example = "TRANSFERENCIA") String tipoTransaccion,
            @Schema(example = "2") long cantidad,
            @Schema(example = "135000.00") BigDecimal montoTotal
    ) {
    }

    @Schema(description = "Una operacion con los datos de sus cuentas y clientes")
    public record Operacion(
            @Schema(example = "101") Long transaccionId,
            OffsetDateTime fechaHora,
            @Schema(example = "TRANSFERENCIA") String tipoTransaccion,
            @Schema(example = "EXITOSA") String estado,
            @Schema(example = "100000.00") BigDecimal monto,
            @Schema(example = "Pago arriendo") String descripcion,
            Participante origen,
            @Schema(description = "Nulo en depositos y retiros") Participante destino
    ) {
    }

    @Schema(description = "Cuenta y cliente que participan en una operacion")
    public record Participante(
            @Schema(example = "1234567890") String numeroCuenta,
            @Schema(example = "7") Long clienteId,
            @Schema(example = "Juan Manuel Tabares") String nombreCliente,
            @Schema(example = "CC") String tipoDocumento,
            @Schema(example = "1017654321") String numeroDocumento
    ) {
    }
}

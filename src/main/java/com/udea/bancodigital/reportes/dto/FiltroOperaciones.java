package com.udea.bancodigital.reportes.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * HU20: criterios de un reporte de operaciones. Todos son opcionales; las
 * fechas incluyen el dia completo (en UTC, como los demas reportes).
 */
@Schema(description = "Filtros aplicados al reporte (HU20)")
public record FiltroOperaciones(
        @Schema(example = "2026-10-01") LocalDate fechaInicio,
        @Schema(example = "2026-10-31") LocalDate fechaFin,
        @Schema(description = "Id del cliente (no del usuario)", example = "7") Long clienteId,
        @Schema(example = "1234567890") String numeroCuenta,
        @Schema(description = "Estado de la operacion", example = "EXITOSA") String estado
) {
}

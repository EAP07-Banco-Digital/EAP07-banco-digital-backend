package com.udea.bancodigital.reportes.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * HU20: parametros para generar un reporte consolidado. A diferencia de la
 * consulta, el periodo es obligatorio.
 */
@Schema(description = "Parametros del reporte consolidado de operaciones (HU20)")
public record ReporteOperacionesRequest(
        @NotNull(message = "La fecha de inicio es obligatoria")
        @Schema(example = "2026-10-01", requiredMode = Schema.RequiredMode.REQUIRED) LocalDate fechaInicio,

        @NotNull(message = "La fecha de fin es obligatoria")
        @Schema(example = "2026-10-31", requiredMode = Schema.RequiredMode.REQUIRED) LocalDate fechaFin,

        @Schema(description = "Id del cliente (no del usuario)", example = "7") Long clienteId,
        @Schema(example = "1234567890") String numeroCuenta,
        @Schema(description = "Estado de la operacion", example = "EXITOSA") String estado
) {
    public FiltroOperaciones comoFiltro() {
        return new FiltroOperaciones(fechaInicio, fechaFin, clienteId, numeroCuenta, estado);
    }
}

package com.udea.bancodigital.cuentas.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * HU16: reporte de movimientos de una cuenta en un periodo.
 * Se cumple siempre: saldoFinal = saldoInicial + totalCreditos - totalDebitos.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Reporte de movimientos de una cuenta en un periodo (HU16)")
public class ReporteMovimientosResponse {

    @Schema(example = "1234567890")
    private String numeroCuenta;

    @Schema(example = "AHORROS")
    private String tipoCuenta;

    @Schema(example = "2026-10-01")
    private LocalDate fechaInicio;

    @Schema(example = "2026-10-31")
    private LocalDate fechaFin;

    @Schema(description = "Saldo al comenzar el primer dia del periodo", example = "100000.00")
    private BigDecimal saldoInicial;

    @Schema(description = "Saldo al terminar el ultimo dia del periodo", example = "130000.00")
    private BigDecimal saldoFinal;

    @Schema(description = "Suma de los movimientos que aumentaron el saldo", example = "50000.00")
    private BigDecimal totalCreditos;

    @Schema(description = "Suma de los movimientos que disminuyeron el saldo", example = "20000.00")
    private BigDecimal totalDebitos;

    @Schema(description = "Movimientos del periodo en orden cronologico; lista vacia si no hubo")
    private List<MovimientoReporteResponse> transacciones;
}

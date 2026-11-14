package com.udea.bancodigital.cuentas.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * HU16: un movimiento dentro del reporte, visto desde la cuenta reportada.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Movimiento de la cuenta dentro del reporte (HU16)")
public class MovimientoReporteResponse {

    @Schema(example = "101")
    private Long transaccionId;

    private OffsetDateTime fechaHora;

    @Schema(example = "TRANSFERENCIA")
    private String tipoTransaccion;

    @Schema(description = "CREDITO si aumento el saldo de la cuenta, DEBITO si lo disminuyo", example = "DEBITO")
    private String naturaleza;

    @Schema(example = "20000.00")
    private BigDecimal monto;

    @Schema(example = "Pago arriendo")
    private String descripcion;

    @Schema(example = "1234567890")
    private String numeroCuentaOrigen;

    @Schema(description = "Nulo en depositos y retiros", example = "9876543210")
    private String numeroCuentaDestino;

    @Schema(description = "Saldo de la cuenta justo despues de este movimiento", example = "80000.00")
    private BigDecimal saldoResultante;
}

package com.udea.bancodigital.transferencias.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Respuesta con los topes de transferencia del cliente o predeterminados del banco")
public class TopeTransferenciaResponse {

    @Schema(description = "Monto máximo permitido por transferencia individual", example = "5000000.00")
    private BigDecimal montoMaximoPorTransferencia;

    @Schema(description = "Monto máximo acumulado permitido en el periodo", example = "10000000.00")
    private BigDecimal montoMaximoAcumuladoPeriodo;

    @Schema(description = "Periodo de aplicación de los topes (ej. DIARIO)", example = "DIARIO")
    private String periodo;

    @Schema(description = "Indica si los topes corresponden a una configuración personalizada del cliente", example = "false")
    private boolean esPersonalizado;
}

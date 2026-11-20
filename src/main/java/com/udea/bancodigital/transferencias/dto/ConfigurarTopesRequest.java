package com.udea.bancodigital.transferencias.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud de configuración de topes de transferencia globales del banco (HU18)")
public class ConfigurarTopesRequest {

    @NotNull(message = "El monto máximo por transferencia es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto máximo por transferencia debe ser mayor a 0")
    @Digits(integer = 13, fraction = 2, message = "El monto máximo por transferencia admite máximo 13 dígitos enteros y 2 decimales")
    @Schema(description = "Monto máximo permitido por transferencia individual", example = "5000000.00")
    private BigDecimal montoMaximoPorTransferencia;

    @NotNull(message = "El límite acumulado del periodo es obligatorio")
    @DecimalMin(value = "0.01", message = "El límite acumulado del periodo debe ser mayor a 0")
    @Digits(integer = 13, fraction = 2, message = "El límite acumulado admite máximo 13 dígitos enteros y 2 decimales")
    @Schema(description = "Límite máximo acumulado permitido en el periodo", example = "10000000.00")
    private BigDecimal montoMaximoAcumuladoPeriodo;
}

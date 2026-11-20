package com.udea.bancodigital.cuentas.dto;

import jakarta.validation.constraints.Digits;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OperacionCuentaRequest {

    // Mismo límite que numeric(15,2) y que TransferenciaRequest.
    // No lleva @NotNull ni @Positive a propósito: nulo y <= 0 los rechaza el
    // servicio con INVALID_AMOUNT, que es el contrato actual.
    @Digits(integer = 13, fraction = 2,
            message = "El monto admite máximo 13 dígitos enteros y 2 decimales")
    private BigDecimal monto;
}
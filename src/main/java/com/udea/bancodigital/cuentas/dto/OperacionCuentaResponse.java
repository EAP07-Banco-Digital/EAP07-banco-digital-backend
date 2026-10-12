package com.udea.bancodigital.cuentas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OperacionCuentaResponse {
    private Long transaccionId;
    private String numeroCuenta;
    private BigDecimal monto;
    private BigDecimal saldoNuevo;
    private String estado;
    private OffsetDateTime fechaHora;
}

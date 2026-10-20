package com.udea.bancodigital.cuentas.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class CuentaResumenResponse {
    private Long id;
    private String numeroCuenta;
    private String tipoCuenta;
    private String estado;
    private OffsetDateTime fechaApertura;
}
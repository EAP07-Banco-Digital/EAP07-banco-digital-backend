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
public class HistorialTransaccionResponse {
    private Long transaccionId;
    private OffsetDateTime fechaHora;
    private String tipoTransaccion;
    private BigDecimal monto;
    private String descripcion;
    private String numeroCuentaOrigen;
    private String numeroCuentaDestino;
    private String tipoMovimiento; // "ENTRADA" o "SALIDA"
}

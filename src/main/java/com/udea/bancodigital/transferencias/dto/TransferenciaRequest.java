package com.udea.bancodigital.transferencias.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransferenciaRequest {

    @NotBlank(message = "El número de cuenta de origen es obligatorio")
    private String numeroCuentaOrigen;

    @NotBlank(message = "El número de cuenta de destino es obligatorio")
    private String numeroCuentaDestino;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
    // Los saldos son numeric(15,2): con mas de 2 decimales la base redondearia
    // en silencio, y con mas de 13 enteros la peticion terminaba en un 500.
    @Digits(integer = 13, fraction = 2, message = "El monto admite máximo 13 dígitos enteros y 2 decimales")
    private BigDecimal monto;

    // La columna es varchar(200).
    @Size(max = 200, message = "La descripción no puede superar 200 caracteres")
    private String descripcion;
}

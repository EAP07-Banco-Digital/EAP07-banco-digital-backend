package com.udea.bancodigital.usuarios.dto;

import jakarta.validation.constraints.NotBlank;

// DTO de entrada del cambio de rol (HU9).
public class CambiarRolRequestDTO {

    @NotBlank(message = MensajesRol.ROL_OBLIGATORIO)
    private String rol;

    public CambiarRolRequestDTO() {
    }

    public CambiarRolRequestDTO(String rol) {
        this.rol = rol;
    }

    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }
}

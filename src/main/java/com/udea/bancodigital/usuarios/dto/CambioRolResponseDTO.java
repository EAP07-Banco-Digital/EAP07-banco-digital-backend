package com.udea.bancodigital.usuarios.dto;

// DTO de salida del cambio de rol (HU9): solo lo necesario para confirmar
// el cambio, nunca la entidad Usuario.
public class CambioRolResponseDTO {

    private String message;
    private Long usuarioId;
    private String email;
    private String rol;

    public CambioRolResponseDTO() {
    }

    public CambioRolResponseDTO(String message, Long usuarioId, String email, String rol) {
        this.message = message;
        this.usuarioId = usuarioId;
        this.email = email;
        this.rol = rol;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }
}

package com.udea.bancodigital.usuarios.dto;

// DTO de salida del cierre de sesion (HU8).
public class LogoutResponseDTO {

    private String message;

    public LogoutResponseDTO() {
    }

    public LogoutResponseDTO(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

package com.udea.bancodigital.usuarios.dto;

// DTO de salida: lo que el backend responde tras un login exitoso.
// Nunca se expone la entidad Usuario directamente (evita filtrar passwordHash).
public class LoginResponseDTO {

    private String token;
    private String tipo;
    private String rol;

    public LoginResponseDTO() {
    }

    public LoginResponseDTO(String token, String tipo) {
        this(token, tipo, null);
    }

    public LoginResponseDTO(String token, String tipo, String rol) {
        this.token = token;
        this.tipo = tipo;
        this.rol = rol;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }
}

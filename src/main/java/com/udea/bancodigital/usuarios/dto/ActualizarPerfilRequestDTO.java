package com.udea.bancodigital.usuarios.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ActualizarPerfilRequestDTO {

    // Mismo largo que las columnas (varchar 255): mas largo terminaba en un 500.
    private static final int LARGO_COLUMNA = 255;

    @NotBlank(message = MensajesPerfil.CAMPO_VACIO)
    @Size(max = LARGO_COLUMNA, message = MensajesRegistro.CAMPO_DEMASIADO_LARGO)
    private String telefono;

    @NotBlank(message = MensajesPerfil.CAMPO_VACIO)
    @Email(message = MensajesRegistro.CORREO_INVALIDO)
    @Size(max = LARGO_COLUMNA, message = MensajesRegistro.CAMPO_DEMASIADO_LARGO)
    private String email;

    @NotBlank(message = MensajesPerfil.CAMPO_VACIO)
    @Size(max = LARGO_COLUMNA, message = MensajesRegistro.CAMPO_DEMASIADO_LARGO)
    private String direccion;

    // Solo existe para detectar el intento de edicion: si llega con cualquier
    // valor, la peticion se rechaza. El documento no es editable.
    private String documentoIdentidad;

    public ActualizarPerfilRequestDTO() {
    }

    public ActualizarPerfilRequestDTO(String telefono, String email,
                                      String direccion, String documentoIdentidad) {
        this.telefono = telefono;
        this.email = email;
        this.direccion = direccion;
        this.documentoIdentidad = documentoIdentidad;
    }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getDocumentoIdentidad() { return documentoIdentidad; }
    public void setDocumentoIdentidad(String documentoIdentidad) { this.documentoIdentidad = documentoIdentidad; }
}

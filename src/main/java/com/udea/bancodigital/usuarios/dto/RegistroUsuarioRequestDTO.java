package com.udea.bancodigital.usuarios.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class RegistroUsuarioRequestDTO {

    // Los @Size reflejan el largo de las columnas (varchar 255): sin ellos, un
    // texto mas largo llegaba a la base y la peticion terminaba en un 500.
    private static final int LARGO_COLUMNA = 255;

    @NotBlank(message = MensajesRegistro.CAMPOS_OBLIGATORIOS)
    @Size(max = LARGO_COLUMNA, message = MensajesRegistro.CAMPO_DEMASIADO_LARGO)
    private String nombres;

    @NotBlank(message = MensajesRegistro.CAMPOS_OBLIGATORIOS)
    @Size(max = LARGO_COLUMNA, message = MensajesRegistro.CAMPO_DEMASIADO_LARGO)
    private String apellidos;

    @NotBlank(message = MensajesRegistro.CAMPOS_OBLIGATORIOS)
    @Size(max = LARGO_COLUMNA, message = MensajesRegistro.CAMPO_DEMASIADO_LARGO)
    private String tipoDocumento;

    @NotBlank(message = MensajesRegistro.CAMPOS_OBLIGATORIOS)
    @Size(max = LARGO_COLUMNA, message = MensajesRegistro.CAMPO_DEMASIADO_LARGO)
    private String numeroDocumento;

    @NotBlank(message = MensajesRegistro.CAMPOS_OBLIGATORIOS)
    @Email(message = MensajesRegistro.CORREO_INVALIDO)
    @Size(max = LARGO_COLUMNA, message = MensajesRegistro.CAMPO_DEMASIADO_LARGO)
    private String email;

    @NotNull(message = MensajesRegistro.CAMPOS_OBLIGATORIOS)
    private LocalDate fechaNacimiento;

    @NotBlank(message = MensajesRegistro.CAMPOS_OBLIGATORIOS)
    @Size(max = LARGO_COLUMNA, message = MensajesRegistro.CAMPO_DEMASIADO_LARGO)
    private String telefono;

    @NotBlank(message = MensajesRegistro.CAMPOS_OBLIGATORIOS)
    @Size(max = LARGO_COLUMNA, message = MensajesRegistro.CAMPO_DEMASIADO_LARGO)
    private String direccion;

    // Minimo 8 caracteres, con al menos una mayuscula, un numero y un simbolo.
    @NotBlank(message = MensajesRegistro.CAMPOS_OBLIGATORIOS)
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$",
            message = MensajesRegistro.PASSWORD_INSEGURA
    )
    private String password;

    @NotBlank(message = MensajesRegistro.CAMPOS_OBLIGATORIOS)
    private String rol;

    public RegistroUsuarioRequestDTO() {
    }

    public RegistroUsuarioRequestDTO(String nombres, String apellidos, String tipoDocumento,
                                     String numeroDocumento, String email, LocalDate fechaNacimiento,
                                     String telefono, String direccion, String password, String rol) {
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.tipoDocumento = tipoDocumento;
        this.numeroDocumento = numeroDocumento;
        this.email = email;
        this.fechaNacimiento = fechaNacimiento;
        this.telefono = telefono;
        this.direccion = direccion;
        this.password = password;
        this.rol = rol;
    }

    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }

    public String getApellidos() { return apellidos; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }

    public String getTipoDocumento() { return tipoDocumento; }
    public void setTipoDocumento(String tipoDocumento) { this.tipoDocumento = tipoDocumento; }

    public String getNumeroDocumento() { return numeroDocumento; }
    public void setNumeroDocumento(String numeroDocumento) { this.numeroDocumento = numeroDocumento; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public LocalDate getFechaNacimiento() { return fechaNacimiento; }
    public void setFechaNacimiento(LocalDate fechaNacimiento) { this.fechaNacimiento = fechaNacimiento; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }
}

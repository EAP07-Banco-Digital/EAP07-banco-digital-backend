package com.udea.bancodigital.usuarios.dto;

// Los textos son criterios de aceptacion de la HU1: deben viajar al cliente
// exactamente asi. Viven aqui porque las anotaciones de validacion exigen
// constantes en tiempo de compilacion y el handler de errores los prioriza.
public final class MensajesRegistro {

    public static final String CAMPOS_OBLIGATORIOS = "Complete todos los campos obligatorios.";
    public static final String CORREO_INVALIDO = "Ingrese un correo electrónico válido.";
    public static final String PASSWORD_INSEGURA = "La contraseña no cumple con los requisitos de seguridad.";
    public static final String CORREO_DUPLICADO = "El correo ingresado ya está registrado.";
    public static final String ROL_NO_PERMITIDO =
            "Solo puedes registrarte como CLIENTE. Otros roles los asigna un administrador.";
    public static final String REGISTRO_EXITOSO = "El usuario ha sido registrado con éxito.";

    private MensajesRegistro() {
    }
}

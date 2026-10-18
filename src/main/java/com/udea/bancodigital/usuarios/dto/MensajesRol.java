package com.udea.bancodigital.usuarios.dto;

// Textos de la HU9 (gestion de roles). Separados de MensajesPerfil porque
// este flujo lo ejecuta un administrador sobre otro usuario, no el propio cliente.
public final class MensajesRol {

    public static final String ROL_OBLIGATORIO = "Debe indicar el nuevo rol.";
    public static final String ROL_ACTUALIZADO = "El rol del usuario fue actualizado.";
    public static final String CAMBIO_PROPIO_ROL =
            "No puedes modificar tu propio rol.";
    public static final String USUARIO_NO_ENCONTRADO =
            "El usuario indicado no existe.";

    private MensajesRol() {
    }
}

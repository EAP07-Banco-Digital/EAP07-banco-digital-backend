package com.udea.bancodigital.usuarios.service;

import java.util.Locale;

// Un correo se guarda y se busca siempre de la misma forma: sin espacios y en
// minusculas. Si el registro guardara "Juan@x.com" y el login buscara
// "juan@x.com", la busqueda exacta en la base no lo encontraria.
final class Correos {

    private Correos() {
    }

    static String normalizar(String correo) {
        // Locale.ROOT: con el locale turco, "I".toLowerCase() no da "i".
        return correo.trim().toLowerCase(Locale.ROOT);
    }
}

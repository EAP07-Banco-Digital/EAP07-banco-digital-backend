package com.udea.bancodigital.shared.exception;

// Codigos de error que comparten varios modulos. Viven en un solo lugar para
// que no vuelvan a convivir dos nombres para el mismo error (DEF-HU5-01).
public final class CodigosError {

    // Peticion sin autenticacion: sin token, token invalido o sin usuario
    // resuelto. Es el valor oficial del backlog.
    public static final String UNAUTHORIZED = "UNAUTHORIZED";

    private CodigosError() {
    }
}

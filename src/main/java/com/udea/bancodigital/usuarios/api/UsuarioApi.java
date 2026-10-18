package com.udea.bancodigital.usuarios.api;

import java.util.Optional;

/**
 * API pública del módulo de Usuarios.
 * <p>
 * Los demás módulos (cuentas, transferencias) solo deben depender de esta interfaz,
 * nunca de las clases internas del módulo (UsuarioService, UsuarioRepository, etc.).
 * Esto garantiza el encapsulamiento del módulo y permite que sus internos cambien
 * sin afectar a los consumidores.
 */
public interface UsuarioApi {

    /**
     * Obtiene el ID del cliente asociado al usuario autenticado dado su email.
     *
     * @param email email extraído del JWT
     * @return el clienteId asociado
     */
    Long obtenerIdClientePorEmail(String email);

    /**
     * Correo y rol vigentes de un usuario, tal como están en la base.
     */
    record Identidad(String email, String rol) {}

    /**
     * Obtiene el correo y el rol actuales del usuario (ej. "CLIENTE", "ADMIN").
     * <p>
     * Lo usa el filtro JWT en cada petición: el token solo trae el id, y el
     * correo y el rol se leen de la base. Así un cambio de rol (HU9) aplica de
     * inmediato y un cambio de correo (HU7) no invalida la sesión abierta.
     *
     * @param usuarioId id del usuario extraído del JWT
     * @return la identidad, o vacío si el usuario no existe o no está activo
     */
    Optional<Identidad> obtenerIdentidadPorId(Long usuarioId);
}

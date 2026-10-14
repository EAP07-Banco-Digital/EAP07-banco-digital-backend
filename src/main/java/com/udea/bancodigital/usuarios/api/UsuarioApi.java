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
     * Obtiene el nombre del rol actual del usuario (ej. "CLIENTE", "ADMIN").
     * <p>
     * Lo usa el filtro JWT en cada petición: el rol se lee de la base y no del
     * token, así un cambio de rol (HU9) aplica de inmediato.
     *
     * @param email email extraído del JWT
     * @return el rol, o vacío si el usuario ya no existe
     */
    Optional<String> obtenerRolPorEmail(String email);
}

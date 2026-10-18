package com.udea.bancodigital.usuarios.controller;

import com.udea.bancodigital.usuarios.dto.ActualizarPerfilRequestDTO;
import com.udea.bancodigital.usuarios.dto.CambiarRolRequestDTO;
import com.udea.bancodigital.usuarios.dto.CambioRolResponseDTO;
import com.udea.bancodigital.usuarios.dto.PerfilUsuarioDTO;
import com.udea.bancodigital.usuarios.dto.RegistroUsuarioRequestDTO;
import com.udea.bancodigital.usuarios.dto.RegistroUsuarioResponseDTO;
import com.udea.bancodigital.usuarios.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/usuarios")
@Tag(name = "Usuarios", description = "Gestión de usuarios: registro, consulta y actualización de perfil")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @Operation(
            summary = "Registrar nuevo usuario",
            description = "Crea un nuevo cliente en el sistema. Este endpoint es público y no requiere autenticación."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario registrado exitosamente.",
                    content = @Content(schema = @Schema(implementation = RegistroUsuarioResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos (campos requeridos faltantes o formato incorrecto).",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Se pidió un rol distinto de CLIENTE (ROLE_NOT_ALLOWED); "
                    + "los demás roles solo los asigna un administrador.", content = @Content),
            @ApiResponse(responseCode = "409", description = "El email ya está registrado en el sistema.",
                    content = @Content)
    })
    @SecurityRequirements  // Registro es público
    @PostMapping("/registro")
    public ResponseEntity<RegistroUsuarioResponseDTO> registrar(
            @Valid @RequestBody RegistroUsuarioRequestDTO request) {

        RegistroUsuarioResponseDTO response = usuarioService.registrar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Consultar perfil del usuario autenticado",
            description = "Retorna los datos del perfil del cliente que está autenticado. La identidad se extrae directamente del token JWT."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil retornado exitosamente.",
                    content = @Content(schema = @Schema(implementation = PerfilUsuarioDTO.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado. Se requiere token JWT.", content = @Content)
    })
    // La identidad sale del token, nunca de la URL: asi un cliente no puede
    // pedir ni editar el perfil de otro cambiando un id.
    @GetMapping("/me")
    public PerfilUsuarioDTO consultarPerfil(Principal principal) {
        return usuarioService.consultarPerfil(principal.getName());
    }

    @Operation(
            summary = "Actualizar perfil del usuario autenticado",
            description = "Actualiza los datos del perfil del cliente autenticado. Solo puede modificar su propia información."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil actualizado exitosamente.",
                    content = @Content(schema = @Schema(implementation = PerfilUsuarioDTO.class))),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos.", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado. Se requiere token JWT.", content = @Content)
    })
    @PutMapping("/me")
    public PerfilUsuarioDTO actualizarPerfil(
            Principal principal, @Valid @RequestBody ActualizarPerfilRequestDTO request) {

        return usuarioService.actualizarPerfil(principal.getName(), request);
    }

    @Operation(
            summary = "Cambiar el rol de un usuario",
            description = "Asigna un nuevo rol al usuario indicado. Solo disponible para administradores; "
                    + "un administrador no puede modificar su propio rol."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rol actualizado exitosamente.",
                    content = @Content(schema = @Schema(implementation = CambioRolResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Rol inválido o intento de modificar el propio rol "
                    + "(SELF_ROLE_CHANGE_NOT_ALLOWED).", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado. Se requiere token JWT.", content = @Content),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no es administrador (FORBIDDEN).",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "El usuario indicado no existe.", content = @Content)
    })
    // La restriccion a ADMIN vive en SecurityConfig; aqui el token solo aporta
    // quien hace el cambio, para impedir que se lo haga a si mismo.
    @PutMapping("/{id}/rol")
    public CambioRolResponseDTO cambiarRol(
            Principal principal, @PathVariable Long id, @Valid @RequestBody CambiarRolRequestDTO request) {

        return usuarioService.cambiarRol(principal.getName(), id, request);
    }

    @Operation(summary = "Verificar autenticación", description = "Endpoint de prueba para verificar que el token JWT es válido.")
    @ApiResponse(responseCode = "200", description = "El usuario está autenticado correctamente.")
    @GetMapping("/prueba")
    public String prueba() {
        return "Usuario autenticado correctamente";
    }
}

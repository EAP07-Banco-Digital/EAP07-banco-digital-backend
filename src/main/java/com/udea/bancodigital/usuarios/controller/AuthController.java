package com.udea.bancodigital.usuarios.controller;

import com.udea.bancodigital.shared.jwt.JwtAuthenticationFilter;
import com.udea.bancodigital.usuarios.dto.LoginRequestDTO;
import com.udea.bancodigital.usuarios.dto.LoginResponseDTO;
import com.udea.bancodigital.usuarios.dto.LogoutResponseDTO;
import com.udea.bancodigital.usuarios.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación", description = "Endpoints para la autenticación de usuarios (login y logout)")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(
            summary = "Iniciar sesión",
            description = "Autentica las credenciales del usuario y devuelve un token JWT válido por 1 hora."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autenticación exitosa. Retorna el token JWT.",
                    content = @Content(schema = @Schema(implementation = LoginResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Credenciales inválidas (email o contraseña incorrectos).",
                    content = @Content)
    })
    @SecurityRequirements  // Este endpoint es público, no requiere token
    @PostMapping("/login")
    public LoginResponseDTO login(@Valid @RequestBody LoginRequestDTO request) {
        return authService.login(request);
    }

    @Operation(
            summary = "Cerrar sesión",
            description = "Revoca el token JWT con el que se hace la petición. A partir de ese momento "
                    + "cualquier petición protegida con ese token responde 401 TOKEN_REVOKED."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesión cerrada. El token quedó revocado.",
                    content = @Content(schema = @Schema(implementation = LogoutResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Sin token, token inválido o token ya revocado.",
                    content = @Content)
    })
    @PostMapping("/logout")
    public LogoutResponseDTO logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        // La ruta es protegida: si se llega aqui, el filtro JWT ya autentico
        // este mismo header, asi que trae el prefijo Bearer.
        return authService.logout(authorization.substring(JwtAuthenticationFilter.PREFIJO_BEARER.length()));
    }
}

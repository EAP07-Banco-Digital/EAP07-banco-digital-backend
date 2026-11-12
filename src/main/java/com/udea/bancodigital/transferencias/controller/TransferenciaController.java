package com.udea.bancodigital.transferencias.controller;

import com.udea.bancodigital.transferencias.dto.TransferenciaRequest;
import com.udea.bancodigital.transferencias.dto.TransferenciaResponse;
import com.udea.bancodigital.transferencias.service.TransferenciaService;
import com.udea.bancodigital.usuarios.api.UsuarioApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transferencias")
@RequiredArgsConstructor
@Tag(name = "Transferencias", description = "Gestión de transferencias entre cuentas bancarias")
public class TransferenciaController {

    private final TransferenciaService transferenciaService;
    // Única dependencia externa: solo se acopla a la interfaz pública (API) del módulo usuarios
    private final UsuarioApi usuarioApi;

    @Operation(
            summary = "Realizar una transferencia (HU12)",
            description = """
                    Transfiere dinero desde una cuenta del usuario autenticado hacia otra cuenta.
                    
                    Reglas de negocio:
                    - Ambas cuentas deben existir y estar en estado `ACTIVA`.
                    - La cuenta de origen debe pertenecer al usuario autenticado.
                    - La cuenta de origen y destino no pueden ser la misma.
                    - La cuenta de origen debe tener saldo suficiente.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Transferencia realizada exitosamente.",
                    content = @Content(schema = @Schema(implementation = TransferenciaResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos (ej. saldo insuficiente, misma cuenta).", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado. Se requiere token JWT.", content = @Content),
            @ApiResponse(responseCode = "403", description = "La cuenta de origen no pertenece al cliente.", content = @Content),
            @ApiResponse(responseCode = "404", description = "La cuenta de origen (SOURCE_ACCOUNT_NOT_FOUND) o la de destino (DESTINATION_ACCOUNT_NOT_FOUND) no existe.", content = @Content),
            @ApiResponse(responseCode = "409", description = "Alguna de las cuentas no está activa.", content = @Content)
    })
    @PostMapping
    public ResponseEntity<TransferenciaResponse> realizarTransferencia(
            @Valid @RequestBody TransferenciaRequest request) {

        String email = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        Long clienteId = usuarioApi.obtenerIdClientePorEmail(email);

        TransferenciaResponse response = transferenciaService.realizarTransferencia(clienteId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

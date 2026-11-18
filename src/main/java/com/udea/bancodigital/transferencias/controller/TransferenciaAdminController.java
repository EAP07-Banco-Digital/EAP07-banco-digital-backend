package com.udea.bancodigital.transferencias.controller;

import com.udea.bancodigital.transferencias.dto.ConfigurarTopesRequest;
import com.udea.bancodigital.transferencias.dto.TopeTransferenciaResponse;
import com.udea.bancodigital.transferencias.service.TopeTransferenciaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HU18: Configuración administrativa de topes de transferencia.
 * Toda la ruta /api/v1/admin/** exige rol ADMIN en SecurityConfig:
 * un CLIENTE recibe 403 FORBIDDEN y una petición sin token, 401 UNAUTHORIZED.
 */
@RestController
@RequestMapping("/api/v1/admin/transferencias/topes")
@RequiredArgsConstructor
@Tag(name = "Administración de Transferencias", description = "Configuración administrativa de topes de transferencia (solo ADMIN)")
@SecurityRequirement(name = "bearerAuth")
public class TransferenciaAdminController {

    private final TopeTransferenciaService topeTransferenciaService;

    @Operation(
            summary = "Configurar topes de transferencia globales (HU18)",
            description = """
                    Configura los topes globales de transferencia del banco (monto máximo individual y límite acumulado del periodo).
                    Requiere rol ADMIN.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Topes configurados exitosamente.",
                    content = @Content(schema = @Schema(implementation = TopeTransferenciaResponse.class))),
            @ApiResponse(responseCode = "400", description = "Valores inválidos (menores o iguales a cero, o acumulado menor al individual).", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado.", content = @Content),
            @ApiResponse(responseCode = "403", description = "No autorizado (se requiere rol ADMIN).", content = @Content)
    })
    @PutMapping
    public ResponseEntity<TopeTransferenciaResponse> configurarTopes(
            @Valid @RequestBody ConfigurarTopesRequest request) {
        TopeTransferenciaResponse response = topeTransferenciaService.configurarTopesGlobales(request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Configurar topes de transferencia globales vía POST (HU18)",
            description = "Alternativa POST para configurar los topes globales de transferencia del banco."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Topes configurados exitosamente.",
                    content = @Content(schema = @Schema(implementation = TopeTransferenciaResponse.class))),
            @ApiResponse(responseCode = "400", description = "Valores inválidos.", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado.", content = @Content),
            @ApiResponse(responseCode = "403", description = "No autorizado.", content = @Content)
    })
    @PostMapping
    public ResponseEntity<TopeTransferenciaResponse> configurarTopesPost(
            @Valid @RequestBody ConfigurarTopesRequest request) {
        TopeTransferenciaResponse response = topeTransferenciaService.configurarTopesGlobales(request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Consultar topes de transferencia globales (HU18)",
            description = "Consulta la configuración actual de los topes globales del banco. Requiere rol ADMIN."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Topes globales consultados exitosamente.",
                    content = @Content(schema = @Schema(implementation = TopeTransferenciaResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado.", content = @Content),
            @ApiResponse(responseCode = "403", description = "No autorizado (se requiere rol ADMIN).", content = @Content),
            @ApiResponse(responseCode = "503", description = "Los topes de transferencia aún no han sido configurados.", content = @Content)
    })
    @GetMapping
    public ResponseEntity<TopeTransferenciaResponse> consultarTopesGlobales() {
        TopeTransferenciaResponse response = topeTransferenciaService.consultarTopesGlobales();
        return ResponseEntity.ok(response);
    }
}

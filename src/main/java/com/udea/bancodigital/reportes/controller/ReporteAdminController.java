package com.udea.bancodigital.reportes.controller;

import com.udea.bancodigital.reportes.dto.FiltroOperaciones;
import com.udea.bancodigital.reportes.dto.ReporteOperacionesRequest;
import com.udea.bancodigital.reportes.dto.ReporteOperacionesResponse;
import com.udea.bancodigital.reportes.service.ReporteOperacionesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * HU20: reportes administrativos. Toda la ruta /api/v1/admin/** exige rol
 * ADMIN en SecurityConfig: un CLIENTE recibe 403 FORBIDDEN y una peticion sin
 * token, 401 UNAUTHORIZED, antes de llegar aqui.
 */
@RestController
@RequestMapping("/api/v1/admin/reportes")
@RequiredArgsConstructor
@Tag(name = "Reportes administrativos", description = "Consulta y generacion de reportes de operaciones (solo ADMIN)")
@SecurityRequirement(name = "bearerAuth")
public class ReporteAdminController {

    private final ReporteOperacionesService reporteOperacionesService;

    @Operation(
            summary = "Consultar operaciones con filtros (HU20)",
            description = """
                    Devuelve las operaciones de los clientes que cumplen todos los filtros enviados, con un
                    resumen agregado (cantidad, monto total y totales por tipo) y el detalle de cada operacion
                    con sus cuentas y clientes.

                    Todos los filtros son opcionales; las fechas incluyen el dia completo (hora de Colombia). Si nada
                    coincide, responde 200 con totales en 0 y detalle vacio.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reporte generado (puede estar vacio).",
                    content = @Content(schema = @Schema(implementation = ReporteOperacionesResponse.class))),
            @ApiResponse(responseCode = "400", description = "fechaInicio posterior a fechaFin (RANGO_FECHAS_INVALIDO) o parametro mal formado (VALIDATION_ERROR).", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado (UNAUTHORIZED).", content = @Content),
            @ApiResponse(responseCode = "403", description = "El usuario no es ADMIN (FORBIDDEN).", content = @Content)
    })
    @GetMapping("/operaciones")
    public ResponseEntity<ReporteOperacionesResponse> consultarOperaciones(
            @Parameter(description = "Primer dia (YYYY-MM-DD)", example = "2026-10-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @Parameter(description = "Ultimo dia (YYYY-MM-DD), incluido", example = "2026-10-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @Parameter(description = "Id del cliente (no del usuario)", example = "7")
            @RequestParam(required = false) Long clienteId,
            @Parameter(description = "Numero de cuenta (origen o destino)", example = "1234567890")
            @RequestParam(required = false) String numeroCuenta,
            @Parameter(description = "Estado de la operacion", example = "EXITOSA")
            @RequestParam(required = false) String estado) {

        return ResponseEntity.ok(reporteOperacionesService.generar(
                new FiltroOperaciones(fechaInicio, fechaFin, clienteId, numeroCuenta, estado)));
    }

    @Operation(
            summary = "Generar reporte consolidado de operaciones (HU20)",
            description = """
                    Genera el reporte consolidado de un periodo. `fechaInicio` y `fechaFin` son obligatorias;
                    `clienteId`, `numeroCuenta` y `estado` acotan el reporte. Devuelve el mismo resumen y
                    detalle que la consulta.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reporte generado (puede estar vacio).",
                    content = @Content(schema = @Schema(implementation = ReporteOperacionesResponse.class))),
            @ApiResponse(responseCode = "400", description = "Falta el periodo (VALIDATION_ERROR) o fechaInicio es posterior a fechaFin (RANGO_FECHAS_INVALIDO).", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado (UNAUTHORIZED).", content = @Content),
            @ApiResponse(responseCode = "403", description = "El usuario no es ADMIN (FORBIDDEN).", content = @Content)
    })
    @PostMapping("/operaciones")
    public ResponseEntity<ReporteOperacionesResponse> generarReporteOperaciones(
            @Valid @RequestBody ReporteOperacionesRequest request) {

        return ResponseEntity.ok(reporteOperacionesService.generar(request.comoFiltro()));
    }
}

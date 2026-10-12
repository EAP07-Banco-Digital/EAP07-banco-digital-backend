package com.udea.bancodigital.cuentas.controller;

import com.udea.bancodigital.cuentas.dto.AperturaCuentaRequest;
import com.udea.bancodigital.cuentas.dto.CuentaResponse;
import com.udea.bancodigital.cuentas.dto.HistorialTransaccionResponse;
import com.udea.bancodigital.cuentas.dto.OperacionCuentaRequest;
import com.udea.bancodigital.cuentas.dto.OperacionCuentaResponse;
import com.udea.bancodigital.cuentas.service.CuentaService;
import com.udea.bancodigital.usuarios.api.UsuarioApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/cuentas")
@RequiredArgsConstructor
@Tag(name = "Cuentas", description = "Gestión de cuentas bancarias: apertura y consulta de saldo")
public class CuentaController {

    private final CuentaService cuentaService;
    // Única dependencia externa: solo se acopla a la interfaz pública (API) del módulo usuarios
    private final UsuarioApi usuarioApi;

    @Operation(
            summary = "Solicitar apertura de cuenta (HU5)",
            description = """
                    Crea una nueva cuenta bancaria para el cliente autenticado.
                    
                    Tipos de cuenta válidos: `AHORROS` o `CORRIENTE`.
                    
                    El saldo inicial siempre es $0. Un cliente no puede tener dos cuentas activas del mismo tipo.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cuenta creada exitosamente.",
                    content = @Content(schema = @Schema(implementation = CuentaResponse.class))),
            @ApiResponse(responseCode = "400", description = "Tipo de cuenta inválido o datos faltantes.", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado. Se requiere token JWT.", content = @Content),
            @ApiResponse(responseCode = "409", description = "El cliente ya posee una cuenta activa de ese tipo.", content = @Content)
    })
    @PostMapping
    public ResponseEntity<CuentaResponse> solicitarAperturaCuenta(
            @Valid @RequestBody AperturaCuentaRequest request) {

        String email = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        Long clienteId = usuarioApi.obtenerIdClientePorEmail(email);

        CuentaResponse response = cuentaService.solicitarAperturaCuenta(clienteId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Consultar saldo de una cuenta (HU6)",
            description = "Retorna el saldo disponible y el estado actual de la cuenta. Solo el dueño de la cuenta puede consultarla."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Saldo retornado exitosamente. Si la cuenta está BLOQUEADA, también se devuelve HTTP 200.",
                    content = @Content(schema = @Schema(implementation = CuentaResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado. Se requiere token JWT.", content = @Content),
            @ApiResponse(responseCode = "403", description = "La cuenta no pertenece al cliente autenticado.", content = @Content),
            @ApiResponse(responseCode = "404", description = "Cuenta no encontrada.", content = @Content)
    })
    @GetMapping("/{numeroCuenta}/saldo")
    public ResponseEntity<CuentaResponse> consultarSaldo(
            @Parameter(description = "Número de cuenta bancaria a consultar", example = "1234567890", required = true)
            @PathVariable String numeroCuenta) {

        String email = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        Long clienteId = usuarioApi.obtenerIdClientePorEmail(email);

        CuentaResponse response = cuentaService.consultarSaldo(clienteId, numeroCuenta);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Registrar un depósito / crédito en una cuenta (HU14)",
            description = """
                    Incrementa el saldo de la cuenta indicada para el cliente autenticado y registra la transacción como EXITOSA.
                    
                    Reglas de negocio:
                    - La cuenta debe pertenecer al cliente autenticado.
                    - La cuenta debe encontrarse en estado ACTIVA.
                    - El monto debe ser mayor que cero.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Crédito registrado exitosamente.",
                    content = @Content(schema = @Schema(implementation = OperacionCuentaResponse.class))),
            @ApiResponse(responseCode = "400", description = "Monto inválido (INVALID_AMOUNT).", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado. Se requiere token JWT.", content = @Content),
            @ApiResponse(responseCode = "403", description = "La cuenta no pertenece al cliente (FORBIDDEN).", content = @Content),
            @ApiResponse(responseCode = "404", description = "Cuenta no encontrada (ACCOUNT_NOT_FOUND).", content = @Content),
            @ApiResponse(responseCode = "409", description = "La cuenta no se encuentra activa o está bloqueada (ACCOUNT_NOT_ACTIVE).", content = @Content)
    })
    @PostMapping("/{numeroCuenta}/depositos")
    public ResponseEntity<OperacionCuentaResponse> registrarCredito(
            @Parameter(description = "Número de cuenta bancaria", example = "1234567890", required = true)
            @PathVariable String numeroCuenta,
            @RequestBody OperacionCuentaRequest request) {

        String email = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        Long clienteId = usuarioApi.obtenerIdClientePorEmail(email);

        OperacionCuentaResponse response = cuentaService.registrarCredito(clienteId, numeroCuenta, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Registrar un retiro / débito en una cuenta (HU14)",
            description = """
                    Disminuye el saldo de la cuenta indicada para el cliente autenticado y registra la transacción como EXITOSA.
                    
                    Reglas de negocio:
                    - La cuenta debe pertenecer al cliente autenticado.
                    - La cuenta debe encontrarse en estado ACTIVA.
                    - La cuenta debe tener saldo suficiente.
                    - El monto debe ser mayor que cero y menor o igual al saldo disponible.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Débito registrado exitosamente.",
                    content = @Content(schema = @Schema(implementation = OperacionCuentaResponse.class))),
            @ApiResponse(responseCode = "400", description = "Monto inválido (INVALID_AMOUNT) o saldo insuficiente (INSUFFICIENT_FUNDS).", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado. Se requiere token JWT.", content = @Content),
            @ApiResponse(responseCode = "403", description = "La cuenta no pertenece al cliente (FORBIDDEN).", content = @Content),
            @ApiResponse(responseCode = "404", description = "Cuenta no encontrada (ACCOUNT_NOT_FOUND).", content = @Content),
            @ApiResponse(responseCode = "409", description = "La cuenta no se encuentra activa o está bloqueada (ACCOUNT_NOT_ACTIVE).", content = @Content)
    })
    @PostMapping("/{numeroCuenta}/retiros")
    public ResponseEntity<OperacionCuentaResponse> registrarDebito(
            @Parameter(description = "Número de cuenta bancaria", example = "1234567890", required = true)
            @PathVariable String numeroCuenta,
            @RequestBody OperacionCuentaRequest request) {

        String email = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        Long clienteId = usuarioApi.obtenerIdClientePorEmail(email);

        OperacionCuentaResponse response = cuentaService.registrarDebito(clienteId, numeroCuenta, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Consultar historial de transacciones de una cuenta con filtros y paginación (HU15)",
            description = """
                    Retorna el historial paginado de movimientos de la cuenta perteneciente al cliente autenticado.
                    
                    Filtros disponibles:
                    - `desde` (opcional): Fecha inicial (formato YYYY-MM-DD).
                    - `hasta` (opcional): Fecha final (formato YYYY-MM-DD).
                    - `tipoTransaccion` (opcional): Filtro por tipo de transacción (ej. TRANSFERENCIA, CREDITO, DEBITO).
                    - `page` (opcional, default 1): Número de página (base 1).
                    - `size` (opcional, default 10): Tamaño de página.
                    
                    Ordenamiento: Transacciones ordenadas por fechaHora de manera descendente.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historial consultado exitosamente."),
            @ApiResponse(responseCode = "400", description = "Rango de fechas inválido (INVALID_DATE_RANGE) o tipo inválido (INVALID_TRANSACTION_TYPE).", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado. Se requiere token JWT.", content = @Content),
            @ApiResponse(responseCode = "403", description = "La cuenta no pertenece al cliente autenticado (FORBIDDEN).", content = @Content),
            @ApiResponse(responseCode = "404", description = "Cuenta no encontrada (ACCOUNT_NOT_FOUND).", content = @Content),
            @ApiResponse(responseCode = "409", description = "La cuenta se encuentra bloqueada o inactiva (ACCOUNT_NOT_ACTIVE).", content = @Content)
    })
    @GetMapping("/{numeroCuenta}/transacciones")
    public ResponseEntity<Page<HistorialTransaccionResponse>> consultarHistorial(
            @Parameter(description = "Número de cuenta bancaria", example = "1234567890", required = true)
            @PathVariable String numeroCuenta,
            @Parameter(description = "Fecha inicial (formato YYYY-MM-DD)", example = "2026-10-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @Parameter(description = "Fecha final (formato YYYY-MM-DD)", example = "2026-10-11")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @Parameter(description = "Tipo de transacción (ej. TRANSFERENCIA, CREDITO, DEBITO)", example = "CREDITO")
            @RequestParam(required = false) String tipoTransaccion,
            @Parameter(description = "Número de página (base 1)", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Tamaño de página", example = "10")
            @RequestParam(defaultValue = "10") int size) {

        String email = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        Long clienteId = usuarioApi.obtenerIdClientePorEmail(email);

        Page<HistorialTransaccionResponse> historial = cuentaService.consultarHistorial(
                clienteId, numeroCuenta, desde, hasta, tipoTransaccion, page, size
        );

        return ResponseEntity.ok(historial);
    }
}

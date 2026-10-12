package com.udea.bancodigital.cuentas.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.udea.bancodigital.cuentas.dto.OperacionCuentaRequest;
import com.udea.bancodigital.cuentas.dto.OperacionCuentaResponse;
import com.udea.bancodigital.cuentas.service.CuentaService;
import com.udea.bancodigital.shared.config.CustomAuthEntryPoint;
import com.udea.bancodigital.shared.exception.NegocioException;
import com.udea.bancodigital.shared.jwt.JwtAuthenticationFilter;
import com.udea.bancodigital.usuarios.api.UsuarioApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CuentaController.class)
@AutoConfigureMockMvc(addFilters = false)
class CuentaControllerOperacionesTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private CuentaService cuentaService;

    @MockitoBean
    private UsuarioApi usuarioApi;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private CustomAuthEntryPoint customAuthEntryPoint;

    private final String NUMERO_CUENTA = "1234567890";
    private final String EMAIL_AUTENTICADO = "cliente@banco.com";
    private final Long CLIENTE_ID = 1L;

    @BeforeEach
    void setUp() {
        given(usuarioApi.obtenerIdClientePorEmail(EMAIL_AUTENTICADO)).willReturn(CLIENTE_ID);
    }

    @Test
    @WithMockUser(username = "cliente@banco.com")
    void registrarCredito_exitoso_retorna201EIdentificadorTransaccion() throws Exception {
        OperacionCuentaRequest request = new OperacionCuentaRequest(new BigDecimal("100000.00"));
        OperacionCuentaResponse response = OperacionCuentaResponse.builder()
                .transaccionId(101L)
                .numeroCuenta(NUMERO_CUENTA)
                .monto(new BigDecimal("100000.00"))
                .saldoNuevo(new BigDecimal("250000.00"))
                .estado("EXITOSA")
                .fechaHora(OffsetDateTime.now())
                .build();

        given(cuentaService.registrarCredito(eq(CLIENTE_ID), eq(NUMERO_CUENTA), any())).willReturn(response);

        mockMvc.perform(post("/api/v1/cuentas/{numeroCuenta}/depositos", NUMERO_CUENTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transaccionId").value(101L))
                .andExpect(jsonPath("$.numeroCuenta").value(NUMERO_CUENTA))
                .andExpect(jsonPath("$.monto").value(100000.00))
                .andExpect(jsonPath("$.saldoNuevo").value(250000.00))
                .andExpect(jsonPath("$.estado").value("EXITOSA"))
                .andExpect(jsonPath("$.fechaHora").isNotEmpty());
    }

    @Test
    @WithMockUser(username = "cliente@banco.com")
    void registrarDebito_exitoso_retorna201EIdentificadorTransaccion() throws Exception {
        OperacionCuentaRequest request = new OperacionCuentaRequest(new BigDecimal("50000.00"));
        OperacionCuentaResponse response = OperacionCuentaResponse.builder()
                .transaccionId(102L)
                .numeroCuenta(NUMERO_CUENTA)
                .monto(new BigDecimal("50000.00"))
                .saldoNuevo(new BigDecimal("100000.00"))
                .estado("EXITOSA")
                .fechaHora(OffsetDateTime.now())
                .build();

        given(cuentaService.registrarDebito(eq(CLIENTE_ID), eq(NUMERO_CUENTA), any())).willReturn(response);

        mockMvc.perform(post("/api/v1/cuentas/{numeroCuenta}/retiros", NUMERO_CUENTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transaccionId").value(102L))
                .andExpect(jsonPath("$.numeroCuenta").value(NUMERO_CUENTA))
                .andExpect(jsonPath("$.monto").value(50000.00))
                .andExpect(jsonPath("$.saldoNuevo").value(100000.00))
                .andExpect(jsonPath("$.estado").value("EXITOSA"))
                .andExpect(jsonPath("$.fechaHora").isNotEmpty());
    }

    @Test
    @WithMockUser(username = "cliente@banco.com")
    void registrarDebito_sinSaldoSuficiente_retorna400ConInsufficientFunds() throws Exception {
        OperacionCuentaRequest request = new OperacionCuentaRequest(new BigDecimal("500000.00"));

        given(cuentaService.registrarDebito(eq(CLIENTE_ID), eq(NUMERO_CUENTA), any()))
                .willThrow(new NegocioException("INSUFFICIENT_FUNDS", "Saldo insuficiente", HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/v1/cuentas/{numeroCuenta}/retiros", NUMERO_CUENTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    @WithMockUser(username = "cliente@banco.com")
    void registrarOperacion_montoInvalido_retorna400ConInvalidAmount() throws Exception {
        OperacionCuentaRequest request = new OperacionCuentaRequest(new BigDecimal("-10.00"));

        given(cuentaService.registrarCredito(eq(CLIENTE_ID), eq(NUMERO_CUENTA), any()))
                .willThrow(new NegocioException("INVALID_AMOUNT", "El monto de la operación debe ser mayor que cero", HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/v1/cuentas/{numeroCuenta}/depositos", NUMERO_CUENTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_AMOUNT"));
    }

    @Test
    @WithMockUser(username = "cliente@banco.com")
    void registrarOperacion_cuentaNoPerteneceAlCliente_retorna403ConForbidden() throws Exception {
        OperacionCuentaRequest request = new OperacionCuentaRequest(new BigDecimal("50000.00"));

        given(cuentaService.registrarCredito(eq(CLIENTE_ID), eq(NUMERO_CUENTA), any()))
                .willThrow(new NegocioException("FORBIDDEN", "No tiene permisos para operar sobre esta cuenta", HttpStatus.FORBIDDEN));

        mockMvc.perform(post("/api/v1/cuentas/{numeroCuenta}/depositos", NUMERO_CUENTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(username = "cliente@banco.com")
    void registrarOperacion_cuentaBloqueada_retorna409ConAccountNotActive() throws Exception {
        OperacionCuentaRequest request = new OperacionCuentaRequest(new BigDecimal("50000.00"));

        given(cuentaService.registrarDebito(eq(CLIENTE_ID), eq(NUMERO_CUENTA), any()))
                .willThrow(new NegocioException("ACCOUNT_NOT_ACTIVE", "La cuenta se encuentra bloqueada o inactiva", HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/v1/cuentas/{numeroCuenta}/retiros", NUMERO_CUENTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_ACTIVE"));
    }

    @Test
    @WithMockUser(username = "cliente@banco.com")
    void registrarOperacion_cuentaNoExiste_retorna404ConAccountNotFound() throws Exception {
        OperacionCuentaRequest request = new OperacionCuentaRequest(new BigDecimal("50000.00"));

        given(cuentaService.registrarCredito(eq(CLIENTE_ID), eq("9999999999"), any()))
                .willThrow(new NegocioException("ACCOUNT_NOT_FOUND", "La cuenta consultada no existe", HttpStatus.NOT_FOUND));

        mockMvc.perform(post("/api/v1/cuentas/{numeroCuenta}/depositos", "9999999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_FOUND"));
    }
}

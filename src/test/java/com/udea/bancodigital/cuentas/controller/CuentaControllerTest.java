package com.udea.bancodigital.cuentas.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.udea.bancodigital.cuentas.dto.AperturaCuentaRequest;
import com.udea.bancodigital.cuentas.dto.CuentaResponse;
import com.udea.bancodigital.cuentas.service.CuentaService;
import com.udea.bancodigital.shared.config.CustomAccessDeniedHandler;
import com.udea.bancodigital.shared.config.CustomAuthEntryPoint;
import com.udea.bancodigital.shared.exception.NegocioException;
import com.udea.bancodigital.shared.jwt.JwtAuthenticationFilter;
import com.udea.bancodigital.usuarios.api.UsuarioApi;
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
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CuentaController.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(username = "juan@banco.com", roles = "CLIENTE")
class CuentaControllerTest {

    private static final Long CLIENTE_ID = 7L;
    private static final String CORREO = "juan@banco.com";
    private static final String NUMERO = "1000000001";

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

    @MockitoBean
    private CustomAccessDeniedHandler customAccessDeniedHandler;

    private CuentaResponse respuesta(String estado, String saldo) {
        return CuentaResponse.builder()
                .id(1L)
                .clienteId(CLIENTE_ID)
                .numeroCuenta(NUMERO)
                .tipoCuenta("AHORROS")
                .saldoDisponible(new BigDecimal(saldo))
                .estado(estado)
                .fechaApertura(OffsetDateTime.parse("2026-09-20T10:00:00-05:00"))
                .build();
    }

    private void elTokenResuelveAlCliente() {
        given(usuarioApi.obtenerIdClientePorEmail(CORREO)).willReturn(CLIENTE_ID);
    }

    @Test
    void aperturaExitosaDevuelve201YResuelveElClienteDesdeElCorreoDelToken() throws Exception {
        elTokenResuelveAlCliente();
        given(cuentaService.solicitarAperturaCuenta(eq(CLIENTE_ID), any(AperturaCuentaRequest.class)))
                .willReturn(respuesta("ACTIVA", "0"));

        mockMvc.perform(post("/api/v1/cuentas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AperturaCuentaRequest("AHORROS"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroCuenta").value(NUMERO))
                .andExpect(jsonPath("$.tipoCuenta").value("AHORROS"))
                .andExpect(jsonPath("$.saldoDisponible").value(0))
                .andExpect(jsonPath("$.estado").value("ACTIVA"));

        // El clienteId nunca viaja en el cuerpo: sale del token.
        verify(usuarioApi).obtenerIdClientePorEmail(CORREO);
    }

    @Test
    void aperturaConUnTipoDeCuentaNoValidoDevuelve400() throws Exception {
        elTokenResuelveAlCliente();

        mockMvc.perform(post("/api/v1/cuentas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AperturaCuentaRequest("PLAJO"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("El tipo de cuenta debe ser AHORROS o CORRIENTE"));

        verifyNoInteractions(cuentaService);
    }

    @Test
    void aperturaSinTipoDeCuentaDevuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/cuentas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AperturaCuentaRequest(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El tipo de cuenta es obligatorio"));

        verifyNoInteractions(cuentaService);
    }

    @Test
    void aperturaDeUnTipoQueElClienteYaPoseeDevuelve409() throws Exception {
        elTokenResuelveAlCliente();
        given(cuentaService.solicitarAperturaCuenta(eq(CLIENTE_ID), any(AperturaCuentaRequest.class)))
                .willThrow(new NegocioException("ACCOUNT_TYPE_ALREADY_EXISTS",
                        "El cliente ya posee una cuenta activa del tipo AHORROS", HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/v1/cuentas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AperturaCuentaRequest("AHORROS"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_TYPE_ALREADY_EXISTS"));
    }

    @Test
    void consultaDelSaldoDeUnaCuentaPropiaDevuelve200ConSaldoYEstado() throws Exception {
        elTokenResuelveAlCliente();
        given(cuentaService.consultarSaldo(CLIENTE_ID, NUMERO)).willReturn(respuesta("ACTIVA", "500000.00"));

        mockMvc.perform(get("/api/v1/cuentas/{numero}/saldo", NUMERO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoDisponible").value(500000.00))
                .andExpect(jsonPath("$.estado").value("ACTIVA"));

        verify(cuentaService).consultarSaldo(CLIENTE_ID, NUMERO);
    }

    @Test
    void consultaDelSaldoDeUnaCuentaBloqueadaDevuelve200ConSuEstado() throws Exception {
        elTokenResuelveAlCliente();
        given(cuentaService.consultarSaldo(CLIENTE_ID, NUMERO)).willReturn(respuesta("BLOQUEADA", "250000.00"));

        mockMvc.perform(get("/api/v1/cuentas/{numero}/saldo", NUMERO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("BLOQUEADA"));
    }

    @Test
    void consultaDelSaldoDeUnaCuentaAjenaDevuelve403() throws Exception {
        elTokenResuelveAlCliente();
        given(cuentaService.consultarSaldo(CLIENTE_ID, NUMERO)).willThrow(new NegocioException(
                "FORBIDDEN", "No tiene permisos para consultar el saldo de esta cuenta", HttpStatus.FORBIDDEN));

        mockMvc.perform(get("/api/v1/cuentas/{numero}/saldo", NUMERO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void consultaDelSaldoDeUnaCuentaInexistenteDevuelve404() throws Exception {
        elTokenResuelveAlCliente();
        given(cuentaService.consultarSaldo(CLIENTE_ID, "9999999999")).willThrow(new NegocioException(
                "ACCOUNT_NOT_FOUND", "La cuenta consultada no existe", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/v1/cuentas/{numero}/saldo", "9999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_FOUND"));
    }
}

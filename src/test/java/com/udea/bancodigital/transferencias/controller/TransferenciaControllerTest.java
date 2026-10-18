package com.udea.bancodigital.transferencias.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.udea.bancodigital.shared.config.CustomAccessDeniedHandler;
import com.udea.bancodigital.shared.config.CustomAuthEntryPoint;
import com.udea.bancodigital.shared.exception.NegocioException;
import com.udea.bancodigital.shared.jwt.JwtAuthenticationFilter;
import com.udea.bancodigital.transferencias.dto.TransferenciaRequest;
import com.udea.bancodigital.transferencias.dto.TransferenciaResponse;
import com.udea.bancodigital.transferencias.service.TransferenciaService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransferenciaController.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(username = "juan@banco.com", roles = "CLIENTE")
class TransferenciaControllerTest {

    private static final Long CLIENTE_ID = 7L;
    private static final String CORREO = "juan@banco.com";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private TransferenciaService transferenciaService;

    @MockitoBean
    private UsuarioApi usuarioApi;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private CustomAuthEntryPoint customAuthEntryPoint;

    @MockitoBean
    private CustomAccessDeniedHandler customAccessDeniedHandler;

    private TransferenciaRequest solicitud(String origen, String destino, String monto) {
        TransferenciaRequest request = new TransferenciaRequest();
        request.setNumeroCuentaOrigen(origen);
        request.setNumeroCuentaDestino(destino);
        request.setMonto(monto == null ? null : new BigDecimal(monto));
        request.setDescripcion("Pago de prueba");
        return request;
    }

    private void elTokenResuelveAlCliente() {
        given(usuarioApi.obtenerIdClientePorEmail(CORREO)).willReturn(CLIENTE_ID);
    }

    private TransferenciaResponse respuestaExitosa() {
        return TransferenciaResponse.builder()
                .transaccionId(500L)
                .numeroCuentaOrigen("1000000001")
                .numeroCuentaDestino("2000000001")
                .monto(new BigDecimal("100000.00"))
                .estado("EXITOSA")
                .fechaHora(OffsetDateTime.parse("2026-09-20T10:00:00-05:00"))
                .build();
    }

    private org.springframework.test.web.servlet.ResultActions transferir(TransferenciaRequest request)
            throws Exception {
        return mockMvc.perform(post("/api/v1/transferencias")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    @Test
    void transferenciaExitosaDevuelve201YResuelveElClienteDesdeElCorreoDelToken() throws Exception {
        elTokenResuelveAlCliente();
        given(transferenciaService.realizarTransferencia(eq(CLIENTE_ID), any(TransferenciaRequest.class)))
                .willReturn(respuestaExitosa());

        transferir(solicitud("1000000001", "2000000001", "100000.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transaccionId").value(500))
                .andExpect(jsonPath("$.estado").value("EXITOSA"))
                .andExpect(jsonPath("$.monto").value(100000.00));

        verify(usuarioApi).obtenerIdClientePorEmail(CORREO);
    }

    @Test
    void unMontoCeroDevuelve400() throws Exception {
        transferir(solicitud("1000000001", "2000000001", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("El monto debe ser mayor a 0"));

        verifyNoInteractions(transferenciaService);
    }

    @Test
    void unMontoNegativoDevuelve400() throws Exception {
        transferir(solicitud("1000000001", "2000000001", "-500.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El monto debe ser mayor a 0"));
    }

    @Test
    void unMontoAusenteDevuelve400() throws Exception {
        transferir(solicitud("1000000001", "2000000001", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El monto es obligatorio"));
    }

    @Test
    void unaCuentaDeOrigenAusenteDevuelve400() throws Exception {
        transferir(solicitud("", "2000000001", "100000.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El número de cuenta de origen es obligatorio"));
    }

    @Test
    void unaCuentaDeDestinoAusenteDevuelve400() throws Exception {
        transferir(solicitud("1000000001", "  ", "100000.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El número de cuenta de destino es obligatorio"));
    }

    @Test
    void transferirALaMismaCuentaDevuelve400() throws Exception {
        elTokenResuelveAlCliente();
        given(transferenciaService.realizarTransferencia(eq(CLIENTE_ID), any(TransferenciaRequest.class)))
                .willThrow(new NegocioException("SAME_ACCOUNT",
                        "La cuenta de origen y destino no pueden ser la misma", HttpStatus.BAD_REQUEST));

        transferir(solicitud("1000000001", "1000000001", "100000.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SAME_ACCOUNT"));
    }

    @Test
    void transferirSinSaldoSuficienteDevuelve400() throws Exception {
        elTokenResuelveAlCliente();
        given(transferenciaService.realizarTransferencia(eq(CLIENTE_ID), any(TransferenciaRequest.class)))
                .willThrow(new NegocioException("INSUFFICIENT_FUNDS",
                        "Saldo insuficiente para realizar la transferencia", HttpStatus.BAD_REQUEST));

        transferir(solicitud("1000000001", "2000000001", "100000.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void transferirDesdeUnaCuentaAjenaDevuelve403() throws Exception {
        elTokenResuelveAlCliente();
        given(transferenciaService.realizarTransferencia(eq(CLIENTE_ID), any(TransferenciaRequest.class)))
                .willThrow(new NegocioException("FORBIDDEN",
                        "La cuenta de origen no pertenece al usuario autenticado", HttpStatus.FORBIDDEN));

        transferir(solicitud("1000000001", "2000000001", "100000.00"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void transferirHaciaUnaCuentaInexistenteDevuelve404() throws Exception {
        elTokenResuelveAlCliente();
        given(transferenciaService.realizarTransferencia(eq(CLIENTE_ID), any(TransferenciaRequest.class)))
                .willThrow(new NegocioException("DESTINATION_ACCOUNT_NOT_FOUND",
                        "La cuenta 9999999999 no existe", HttpStatus.NOT_FOUND));

        transferir(solicitud("1000000001", "9999999999", "100000.00"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DESTINATION_ACCOUNT_NOT_FOUND"));
    }

    @Test
    void transferirDesdeUnaCuentaBloqueadaDevuelve409() throws Exception {
        elTokenResuelveAlCliente();
        given(transferenciaService.realizarTransferencia(eq(CLIENTE_ID), any(TransferenciaRequest.class)))
                .willThrow(new NegocioException("ACCOUNT_INACTIVE",
                        "La cuenta de origen debe estar ACTIVA", HttpStatus.CONFLICT));

        transferir(solicitud("1000000001", "2000000001", "100000.00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_INACTIVE"));
    }
}

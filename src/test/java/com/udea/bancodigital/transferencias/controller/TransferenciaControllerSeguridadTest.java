package com.udea.bancodigital.transferencias.controller;

import com.udea.bancodigital.shared.config.CustomAccessDeniedHandler;
import com.udea.bancodigital.shared.config.CustomAuthEntryPoint;
import com.udea.bancodigital.shared.config.SecurityConfig;
import com.udea.bancodigital.shared.config.TraceIdFilter;
import com.udea.bancodigital.shared.jwt.JwtAuthenticationFilter;
import com.udea.bancodigital.shared.jwt.JwtService;
import com.udea.bancodigital.shared.jwt.RevocacionTokenService;
import com.udea.bancodigital.transferencias.service.TopeTransferenciaService;
import com.udea.bancodigital.transferencias.service.TransferenciaService;
import com.udea.bancodigital.usuarios.api.UsuarioApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransferenciaController.class)
@Import({SecurityConfig.class, CustomAuthEntryPoint.class, CustomAccessDeniedHandler.class,
        JwtAuthenticationFilter.class, TraceIdFilter.class})
class TransferenciaControllerSeguridadTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransferenciaService transferenciaService;

    @MockitoBean
    private TopeTransferenciaService topeTransferenciaService;

    @MockitoBean
    private UsuarioApi usuarioApi;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RevocacionTokenService revocacionTokenService;

    @Test
    @DisplayName("ESCENARIO 3: Petición sin token a /api/v1/transferencias/topes es rechazada con 401 UNAUTHORIZED")
    void consultarTopesSinTokenResponde401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/transferencias/topes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        verifyNoInteractions(topeTransferenciaService);
    }
}

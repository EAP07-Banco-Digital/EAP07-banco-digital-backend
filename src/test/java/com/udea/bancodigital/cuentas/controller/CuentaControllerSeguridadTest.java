package com.udea.bancodigital.cuentas.controller;

import com.udea.bancodigital.cuentas.service.CuentaService;
import com.udea.bancodigital.shared.config.CustomAccessDeniedHandler;
import com.udea.bancodigital.shared.config.CustomAuthEntryPoint;
import com.udea.bancodigital.shared.config.SecurityConfig;
import com.udea.bancodigital.shared.config.TraceIdFilter;
import com.udea.bancodigital.shared.jwt.JwtAuthenticationFilter;
import com.udea.bancodigital.shared.jwt.JwtService;
import com.udea.bancodigital.shared.jwt.RevocacionTokenService;
import com.udea.bancodigital.usuarios.api.UsuarioApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DEF-HU5-01 sobre la cadena de seguridad real: CuentaControllerTest apaga los
 * filtros, asi que el 401 de la apertura de cuenta solo se puede probar aqui.
 */
@WebMvcTest(CuentaController.class)
@Import({SecurityConfig.class, CustomAuthEntryPoint.class, CustomAccessDeniedHandler.class,
        JwtAuthenticationFilter.class, TraceIdFilter.class})
class CuentaControllerSeguridadTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CuentaService cuentaService;

    @MockitoBean
    private UsuarioApi usuarioApi;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RevocacionTokenService revocacionTokenService;

    @Test
    void abrirCuentaSinHeaderAuthorizationResponde401Unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/cuentas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoCuenta\":\"AHORROS\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Debes iniciar sesion para acceder a este recurso"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        verifyNoInteractions(cuentaService);
    }
}

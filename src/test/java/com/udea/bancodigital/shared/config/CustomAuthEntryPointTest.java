package com.udea.bancodigital.shared.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.udea.bancodigital.shared.jwt.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;

class CustomAuthEntryPointTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CustomAuthEntryPoint entryPoint = new CustomAuthEntryPoint();

    @Test
    void responde401ConElMismoJsonUniformeDelRestoDelBackend() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/usuarios/me");
        MockHttpServletResponse respuesta = new MockHttpServletResponse();

        entryPoint.commence(peticion, respuesta, new InsufficientAuthenticationException("Falta el token"));

        assertThat(respuesta.getStatus()).isEqualTo(401);
        assertThat(respuesta.getContentType()).isEqualTo("application/json");

        JsonNode cuerpo = objectMapper.readTree(respuesta.getContentAsString());
        assertThat(cuerpo.get("errorCode").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(cuerpo.get("message").asText()).isEqualTo("Debes iniciar sesion para acceder a este recurso");
        assertThat(cuerpo.get("details").isNull()).isTrue();
    }

    @Test
    void incluyeElTraceIdQueDejoElFiltroDeTrazas() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/usuarios/me");
        peticion.setAttribute(TraceIdFilter.TRACE_ID_ATTR, "0f1c3a6e-8b2d-4f7a-9c1e-5d3b7a2f4e80");
        MockHttpServletResponse respuesta = new MockHttpServletResponse();

        entryPoint.commence(peticion, respuesta, new InsufficientAuthenticationException("Falta el token"));

        JsonNode cuerpo = objectMapper.readTree(respuesta.getContentAsString());
        assertThat(cuerpo.get("traceId").asText()).isEqualTo("0f1c3a6e-8b2d-4f7a-9c1e-5d3b7a2f4e80");
    }

    @Test
    void sinTraceIdEnElRequestLaRespuestaLoDejaEnNulo() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/usuarios/me");
        MockHttpServletResponse respuesta = new MockHttpServletResponse();

        entryPoint.commence(peticion, respuesta, new InsufficientAuthenticationException("Falta el token"));

        JsonNode cuerpo = objectMapper.readTree(respuesta.getContentAsString());
        assertThat(cuerpo.get("traceId").isNull()).isTrue();
    }

    @Test
    void conUnTokenRevocadoResponde401ConTokenRevoked() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/usuarios/me");
        peticion.setAttribute(JwtAuthenticationFilter.TOKEN_REVOCADO_ATTR, Boolean.TRUE);
        peticion.setAttribute(TraceIdFilter.TRACE_ID_ATTR, "0f1c3a6e-8b2d-4f7a-9c1e-5d3b7a2f4e80");
        MockHttpServletResponse respuesta = new MockHttpServletResponse();

        entryPoint.commence(peticion, respuesta, new InsufficientAuthenticationException("Token revocado"));

        assertThat(respuesta.getStatus()).isEqualTo(401);
        JsonNode cuerpo = objectMapper.readTree(respuesta.getContentAsString());
        assertThat(cuerpo.get("errorCode").asText()).isEqualTo("TOKEN_REVOKED");
        assertThat(cuerpo.get("message").asText()).isEqualTo("La sesion fue cerrada. Inicia sesion de nuevo");
        assertThat(cuerpo.get("details").isNull()).isTrue();
        assertThat(cuerpo.get("traceId").asText()).isEqualTo("0f1c3a6e-8b2d-4f7a-9c1e-5d3b7a2f4e80");
    }
}

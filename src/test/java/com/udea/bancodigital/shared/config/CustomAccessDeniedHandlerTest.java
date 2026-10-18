package com.udea.bancodigital.shared.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

class CustomAccessDeniedHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CustomAccessDeniedHandler handler = new CustomAccessDeniedHandler();

    @Test
    void responde403ConForbiddenYElJsonUniforme() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("PUT", "/api/usuarios/2/rol");
        peticion.setAttribute(TraceIdFilter.TRACE_ID_ATTR, "0f1c3a6e-8b2d-4f7a-9c1e-5d3b7a2f4e80");
        MockHttpServletResponse respuesta = new MockHttpServletResponse();

        handler.handle(peticion, respuesta, new AccessDeniedException("Rol insuficiente"));

        assertThat(respuesta.getStatus()).isEqualTo(403);
        assertThat(respuesta.getContentType()).isEqualTo("application/json");

        JsonNode cuerpo = objectMapper.readTree(respuesta.getContentAsString());
        assertThat(cuerpo.get("errorCode").asText()).isEqualTo("FORBIDDEN");
        assertThat(cuerpo.get("message").asText()).isEqualTo("No tienes permisos para realizar esta operacion");
        assertThat(cuerpo.get("details").isNull()).isTrue();
        assertThat(cuerpo.get("traceId").asText()).isEqualTo("0f1c3a6e-8b2d-4f7a-9c1e-5d3b7a2f4e80");
    }

    @Test
    void sinTraceIdEnElRequestLaRespuestaLoDejaEnNulo() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/usuarios/me");
        MockHttpServletResponse respuesta = new MockHttpServletResponse();

        handler.handle(peticion, respuesta, new AccessDeniedException("Rol insuficiente"));

        JsonNode cuerpo = objectMapper.readTree(respuesta.getContentAsString());
        assertThat(cuerpo.get("traceId").isNull()).isTrue();
    }
}

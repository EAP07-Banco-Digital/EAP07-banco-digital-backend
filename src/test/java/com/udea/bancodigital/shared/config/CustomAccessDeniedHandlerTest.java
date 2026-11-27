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
        assertThat(respuesta.getContentType()).isEqualToIgnoringCase("application/json;charset=UTF-8");
        assertThat(respuesta.getCharacterEncoding()).isEqualToIgnoringCase("utf-8");

        JsonNode cuerpo = objectMapper.readTree(respuesta.getContentAsString());
        assertThat(cuerpo.get("errorCode").asText()).isEqualTo("FORBIDDEN");
        assertThat(cuerpo.get("message").asText()).isEqualTo("No tienes permisos para realizar esta operación");
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

    // DEF-T-04: mismo caso que el 401, con la tilde de "operación".
    @Test
    void escribeEnUtf8AunqueElServidorUseIso88591PorDefecto() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("PUT", "/api/usuarios/2/rol");
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        respuesta.setDefaultCharacterEncoding("ISO-8859-1");

        handler.handle(peticion, respuesta, new AccessDeniedException("Rol insuficiente"));

        assertThat(respuesta.getCharacterEncoding()).isEqualToIgnoringCase("utf-8");
        String cuerpo = new String(respuesta.getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(cuerpo).contains("esta operación");
    }
}

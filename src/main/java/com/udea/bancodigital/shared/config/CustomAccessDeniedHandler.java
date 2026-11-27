package com.udea.bancodigital.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

// Se activa cuando alguien autenticado llama un endpoint reservado a otro rol
// (HU9) -- reemplaza el 403 sin cuerpo que Spring Security manda por defecto.
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws java.io.IOException {

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // Tomcat usa ISO-8859-1 si no se indica; los mensajes llevan tildes (DEF-T-04).
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        Map<String, Object> body = new HashMap<>();
        body.put("errorCode", "FORBIDDEN");
        body.put("message", "No tienes permisos para realizar esta operación");
        body.put("details", null);
        body.put("traceId", request.getAttribute(TraceIdFilter.TRACE_ID_ATTR));

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}

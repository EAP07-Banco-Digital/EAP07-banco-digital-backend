package com.udea.bancodigital.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

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

        Map<String, Object> body = new HashMap<>();
        body.put("errorCode", "FORBIDDEN");
        body.put("message", "No tienes permisos para realizar esta operacion");
        body.put("details", null);
        body.put("traceId", request.getAttribute(TraceIdFilter.TRACE_ID_ATTR));

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}

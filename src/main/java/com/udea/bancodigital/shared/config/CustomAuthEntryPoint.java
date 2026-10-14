package com.udea.bancodigital.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.udea.bancodigital.shared.exception.CodigosError;
import com.udea.bancodigital.shared.jwt.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

// Se activa cuando alguien llama un endpoint protegido sin token
// o con un token invalido o revocado -- reemplaza la respuesta por defecto de Spring.
@Component
public class CustomAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws java.io.IOException {

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        boolean tokenRevocado = Boolean.TRUE.equals(
                request.getAttribute(JwtAuthenticationFilter.TOKEN_REVOCADO_ATTR));

        Map<String, Object> body = new HashMap<>();
        if (tokenRevocado) {
            body.put("errorCode", "TOKEN_REVOKED");
            body.put("message", "La sesion fue cerrada. Inicia sesion de nuevo");
        } else {
            body.put("errorCode", CodigosError.UNAUTHORIZED);
            body.put("message", "Debes iniciar sesion para acceder a este recurso");
        }
        body.put("details", null);
        body.put("traceId", request.getAttribute(TraceIdFilter.TRACE_ID_ATTR));

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}

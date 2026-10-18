package com.udea.bancodigital.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdFilterTest {

    private final TraceIdFilter filtro = new TraceIdFilter();

    @Test
    void dejaUnUuidTrazableEnElRequestYEnElHeader() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/usuarios/me");
        MockHttpServletResponse respuesta = new MockHttpServletResponse();

        filtro.doFilter(peticion, respuesta, new MockFilterChain());

        Object traceId = peticion.getAttribute(TraceIdFilter.TRACE_ID_ATTR);
        assertThat(traceId).isNotNull();
        // El header y el atributo son el mismo valor: asi el cliente puede citar
        // el traceId y del lado del servidor se localiza la peticion.
        assertThat(respuesta.getHeader("X-Trace-Id")).isEqualTo(traceId);
        assertThat(UUID.fromString(traceId.toString())).isNotNull();
    }

    @Test
    void cadaPeticionRecibeUnTraceIdDistinto() throws Exception {
        MockHttpServletRequest primera = new MockHttpServletRequest("GET", "/api/usuarios/me");
        MockHttpServletResponse respuestaPrimera = new MockHttpServletResponse();
        filtro.doFilter(primera, respuestaPrimera, new MockFilterChain());

        MockHttpServletRequest segunda = new MockHttpServletRequest("GET", "/api/v1/cuentas");
        MockHttpServletResponse respuestaSegunda = new MockHttpServletResponse();
        filtro.doFilter(segunda, respuestaSegunda, new MockFilterChain());

        assertThat(respuestaPrimera.getHeader("X-Trace-Id"))
                .isNotEqualTo(respuestaSegunda.getHeader("X-Trace-Id"));
    }

    @Test
    void dejaContinuarLaPeticionSinAlterarLaRespuesta() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/usuarios/me");
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        MockFilterChain cadena = new MockFilterChain();

        filtro.doFilter(peticion, respuesta, cadena);

        assertThat(cadena.getRequest()).isSameAs(peticion);
        assertThat(cadena.getResponse()).isSameAs(respuesta);
        assertThat(respuesta.getStatus()).isEqualTo(200);
    }
}

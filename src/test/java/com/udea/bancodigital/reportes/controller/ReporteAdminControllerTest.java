package com.udea.bancodigital.reportes.controller;

import com.udea.bancodigital.reportes.dto.FiltroOperaciones;
import com.udea.bancodigital.reportes.dto.ReporteOperacionesResponse;
import com.udea.bancodigital.reportes.service.ReporteOperacionesService;
import com.udea.bancodigital.shared.config.CustomAccessDeniedHandler;
import com.udea.bancodigital.shared.config.CustomAuthEntryPoint;
import com.udea.bancodigital.shared.config.SecurityConfig;
import com.udea.bancodigital.shared.config.TraceIdFilter;
import com.udea.bancodigital.shared.exception.NegocioException;
import com.udea.bancodigital.shared.jwt.JwtAuthenticationFilter;
import com.udea.bancodigital.shared.jwt.JwtService;
import com.udea.bancodigital.shared.jwt.RevocacionTokenService;
import com.udea.bancodigital.usuarios.api.UsuarioApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU20 sobre la cadena de seguridad real (SecurityConfig, manejadores 401/403).
 */
@WebMvcTest(ReporteAdminController.class)
@Import({SecurityConfig.class, CustomAuthEntryPoint.class, CustomAccessDeniedHandler.class,
        JwtAuthenticationFilter.class, TraceIdFilter.class})
class ReporteAdminControllerTest {

    private static final String URL = "/api/v1/admin/reportes/operaciones";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReporteOperacionesService reporteOperacionesService;

    @MockitoBean
    private UsuarioApi usuarioApi;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RevocacionTokenService revocacionTokenService;

    private static ReporteOperacionesResponse reporteCon(FiltroOperaciones filtros, int operaciones) {
        List<ReporteOperacionesResponse.Operacion> detalle = operaciones == 0 ? List.of() : List.of(
                new ReporteOperacionesResponse.Operacion(101L, OffsetDateTime.parse("2026-10-10T15:00:00Z"),
                        "TRANSFERENCIA", "EXITOSA", new BigDecimal("100000.00"), "Pago",
                        new ReporteOperacionesResponse.Participante("1000000001", 1L, "Ana Perez", "CC", "111"),
                        new ReporteOperacionesResponse.Participante("2000000002", 2L, "Luis Gomez", "CC", "222")));
        BigDecimal total = operaciones == 0 ? new BigDecimal("0.00") : new BigDecimal("100000.00");
        List<ReporteOperacionesResponse.TotalPorTipo> porTipo = operaciones == 0 ? List.of()
                : List.of(new ReporteOperacionesResponse.TotalPorTipo("TRANSFERENCIA", 1, total));
        return new ReporteOperacionesResponse(OffsetDateTime.parse("2026-11-12T10:00:00Z"), filtros,
                new ReporteOperacionesResponse.Resumen(operaciones, total, porTipo), detalle);
    }

    @Test
    void unAdminConsultaConFiltrosYRecibe200ConResumenYDetalle() throws Exception {
        FiltroOperaciones filtros = new FiltroOperaciones(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 1L, "1000000001", "EXITOSA");
        given(reporteOperacionesService.generar(filtros)).willReturn(reporteCon(filtros, 1));

        mockMvc.perform(get(URL)
                        .with(user("admin@banco.com").roles("ADMIN"))
                        .param("fechaInicio", "2026-10-01")
                        .param("fechaFin", "2026-10-31")
                        .param("clienteId", "1")
                        .param("numeroCuenta", "1000000001")
                        .param("estado", "EXITOSA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filtros.clienteId").value(1))
                .andExpect(jsonPath("$.resumen.totalOperaciones").value(1))
                .andExpect(jsonPath("$.resumen.montoTotal").value(100000.00))
                .andExpect(jsonPath("$.resumen.porTipo[0].tipoTransaccion").value("TRANSFERENCIA"))
                .andExpect(jsonPath("$.operaciones[0].origen.nombreCliente").value("Ana Perez"))
                .andExpect(jsonPath("$.operaciones[0].destino.numeroCuenta").value("2000000002"));

        verify(reporteOperacionesService).generar(filtros);
    }

    @Test
    void unAdminConsultaSinFiltrosYTodosLlegaNulos() throws Exception {
        FiltroOperaciones sinFiltros = new FiltroOperaciones(null, null, null, null, null);
        given(reporteOperacionesService.generar(sinFiltros)).willReturn(reporteCon(sinFiltros, 0));

        mockMvc.perform(get(URL).with(user("admin@banco.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumen.totalOperaciones").value(0))
                .andExpect(jsonPath("$.resumen.montoTotal").value(0))
                .andExpect(jsonPath("$.operaciones").isEmpty());
    }

    @Test
    void unAdminGeneraElReporteConsolidadoPorPost() throws Exception {
        FiltroOperaciones filtros = new FiltroOperaciones(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null, null, null);
        given(reporteOperacionesService.generar(filtros)).willReturn(reporteCon(filtros, 0));

        mockMvc.perform(post(URL)
                        .with(user("admin@banco.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fechaInicio\":\"2026-10-01\",\"fechaFin\":\"2026-10-31\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filtros.fechaInicio").value("2026-10-01"))
                .andExpect(jsonPath("$.resumen.totalOperaciones").value(0))
                .andExpect(jsonPath("$.operaciones").isEmpty());
    }

    @Test
    void elPostSinPeriodoResponde400SinLlegarAlServicio() throws Exception {
        mockMvc.perform(post(URL)
                        .with(user("admin@banco.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.fechaInicio").exists())
                .andExpect(jsonPath("$.details.fechaFin").exists());

        verifyNoInteractions(reporteOperacionesService);
    }

    @Test
    void unRangoInvertidoResponde400RangoFechasInvalido() throws Exception {
        given(reporteOperacionesService.generar(any())).willThrow(new NegocioException(
                "RANGO_FECHAS_INVALIDO", "La fecha de inicio no puede ser posterior a la fecha de fin",
                HttpStatus.BAD_REQUEST));

        mockMvc.perform(get(URL)
                        .with(user("admin@banco.com").roles("ADMIN"))
                        .param("fechaInicio", "2026-10-31")
                        .param("fechaFin", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("RANGO_FECHAS_INVALIDO"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void unClienteRecibe403ForbiddenEnLaConsulta() throws Exception {
        mockMvc.perform(get(URL).with(user("cliente@banco.com").roles("CLIENTE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        verifyNoInteractions(reporteOperacionesService);
    }

    @Test
    void unClienteRecibe403ForbiddenEnLaGeneracion() throws Exception {
        mockMvc.perform(post(URL)
                        .with(user("cliente@banco.com").roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fechaInicio\":\"2026-10-01\",\"fechaFin\":\"2026-10-31\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        verifyNoInteractions(reporteOperacionesService);
    }

    @Test
    void sinTokenLaConsultaResponde401Unauthorized() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        verifyNoInteractions(reporteOperacionesService);
    }

    @Test
    void sinTokenLaGeneracionResponde401Unauthorized() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fechaInicio\":\"2026-10-01\",\"fechaFin\":\"2026-10-31\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verifyNoInteractions(reporteOperacionesService);
    }
}

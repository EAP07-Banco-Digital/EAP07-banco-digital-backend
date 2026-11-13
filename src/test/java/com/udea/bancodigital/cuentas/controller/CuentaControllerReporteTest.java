package com.udea.bancodigital.cuentas.controller;

import com.udea.bancodigital.cuentas.dto.MovimientoReporteResponse;
import com.udea.bancodigital.cuentas.dto.ReporteMovimientosResponse;
import com.udea.bancodigital.cuentas.service.CuentaService;
import com.udea.bancodigital.shared.config.CustomAuthEntryPoint;
import com.udea.bancodigital.shared.exception.NegocioException;
import com.udea.bancodigital.shared.jwt.JwtAuthenticationFilter;
import com.udea.bancodigital.usuarios.api.UsuarioApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU16 en la capa web. La cadena de seguridad (401) se prueba aparte en
 * CuentaControllerSeguridadTest; aqui los filtros estan apagados.
 */
@WebMvcTest(CuentaController.class)
@AutoConfigureMockMvc(addFilters = false)
class CuentaControllerReporteTest {

    private static final String CORREO = "cliente@banco.com";
    private static final Long CLIENTE_ID = 1L;
    private static final String NUMERO = "1234567890";
    private static final String URL = "/api/v1/cuentas/" + NUMERO + "/reporte";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CuentaService cuentaService;

    @MockitoBean
    private UsuarioApi usuarioApi;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private CustomAuthEntryPoint customAuthEntryPoint;

    @BeforeEach
    void elCorreoDelTokenResuelveAlCliente() {
        given(usuarioApi.obtenerIdClientePorEmail(CORREO)).willReturn(CLIENTE_ID);
    }

    @Test
    @WithMockUser(username = CORREO)
    void reporteDeUnaCuentaPropiaResponde200ConSaldosTotalesYDetalle() throws Exception {
        MovimientoReporteResponse movimiento = MovimientoReporteResponse.builder()
                .transaccionId(101L)
                .fechaHora(OffsetDateTime.parse("2026-10-10T15:00:00Z"))
                .tipoTransaccion("TRANSFERENCIA")
                .naturaleza("DEBITO")
                .monto(new BigDecimal("20000.00"))
                .descripcion("Pago arriendo")
                .numeroCuentaOrigen(NUMERO)
                .numeroCuentaDestino("9876543210")
                .saldoResultante(new BigDecimal("80000.00"))
                .build();
        given(cuentaService.generarReporteMovimientos(CLIENTE_ID, NUMERO,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .willReturn(ReporteMovimientosResponse.builder()
                        .numeroCuenta(NUMERO)
                        .tipoCuenta("AHORROS")
                        .fechaInicio(LocalDate.of(2026, 10, 1))
                        .fechaFin(LocalDate.of(2026, 10, 31))
                        .saldoInicial(new BigDecimal("100000.00"))
                        .saldoFinal(new BigDecimal("80000.00"))
                        .totalCreditos(new BigDecimal("0.00"))
                        .totalDebitos(new BigDecimal("20000.00"))
                        .transacciones(List.of(movimiento))
                        .build());

        mockMvc.perform(get(URL).param("fechaInicio", "2026-10-01").param("fechaFin", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroCuenta").value(NUMERO))
                .andExpect(jsonPath("$.fechaInicio").value("2026-10-01"))
                .andExpect(jsonPath("$.fechaFin").value("2026-10-31"))
                .andExpect(jsonPath("$.saldoInicial").value(100000.00))
                .andExpect(jsonPath("$.saldoFinal").value(80000.00))
                .andExpect(jsonPath("$.totalCreditos").value(0.00))
                .andExpect(jsonPath("$.totalDebitos").value(20000.00))
                .andExpect(jsonPath("$.transacciones.length()").value(1))
                .andExpect(jsonPath("$.transacciones[0].transaccionId").value(101))
                .andExpect(jsonPath("$.transacciones[0].naturaleza").value("DEBITO"))
                .andExpect(jsonPath("$.transacciones[0].saldoResultante").value(80000.00));
    }

    @Test
    @WithMockUser(username = CORREO)
    void unPeriodoSinMovimientosResponde200ConListaVacia() throws Exception {
        given(cuentaService.generarReporteMovimientos(eq(CLIENTE_ID), eq(NUMERO), any(), any()))
                .willReturn(ReporteMovimientosResponse.builder()
                        .numeroCuenta(NUMERO)
                        .saldoInicial(new BigDecimal("50000.00"))
                        .saldoFinal(new BigDecimal("50000.00"))
                        .totalCreditos(new BigDecimal("0.00"))
                        .totalDebitos(new BigDecimal("0.00"))
                        .transacciones(List.of())
                        .build());

        mockMvc.perform(get(URL).param("fechaInicio", "2026-01-01").param("fechaFin", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCreditos").value(0))
                .andExpect(jsonPath("$.totalDebitos").value(0))
                .andExpect(jsonPath("$.transacciones").isArray())
                .andExpect(jsonPath("$.transacciones").isEmpty());
    }

    @Test
    @WithMockUser(username = CORREO)
    void unRangoInvertidoResponde400RangoFechasInvalido() throws Exception {
        given(cuentaService.generarReporteMovimientos(eq(CLIENTE_ID), eq(NUMERO), any(), any()))
                .willThrow(new NegocioException("RANGO_FECHAS_INVALIDO",
                        "La fecha de inicio no puede ser posterior a la fecha de fin", HttpStatus.BAD_REQUEST));

        mockMvc.perform(get(URL).param("fechaInicio", "2026-10-31").param("fechaFin", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("RANGO_FECHAS_INVALIDO"))
                .andExpect(jsonPath("$.message").value("La fecha de inicio no puede ser posterior a la fecha de fin"));
    }

    @Test
    @WithMockUser(username = CORREO)
    void elReporteDeUnaCuentaAjenaResponde403() throws Exception {
        given(cuentaService.generarReporteMovimientos(eq(CLIENTE_ID), eq(NUMERO), any(), any()))
                .willThrow(new NegocioException("FORBIDDEN",
                        "No tiene permisos para generar el reporte de esta cuenta", HttpStatus.FORBIDDEN));

        mockMvc.perform(get(URL).param("fechaInicio", "2026-10-01").param("fechaFin", "2026-10-31"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(username = CORREO)
    void unaFechaMalFormadaResponde400SinLlegarAlServicio() throws Exception {
        mockMvc.perform(get(URL).param("fechaInicio", "2026-13-45").param("fechaFin", "2026-10-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verifyNoInteractions(cuentaService);
    }
}

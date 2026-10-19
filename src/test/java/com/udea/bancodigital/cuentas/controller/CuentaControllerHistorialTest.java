package com.udea.bancodigital.cuentas.controller;

import com.udea.bancodigital.cuentas.dto.HistorialTransaccionResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CuentaController.class)
@AutoConfigureMockMvc(addFilters = false)
class CuentaControllerHistorialTest {

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

    private final String NUMERO_CUENTA = "1234567890";
    private final String EMAIL_AUTENTICADO = "cliente@banco.com";
    private final Long CLIENTE_ID = 1L;

    @BeforeEach
    void setUp() {
        given(usuarioApi.obtenerIdClientePorEmail(EMAIL_AUTENTICADO)).willReturn(CLIENTE_ID);
    }

    @Test
    @WithMockUser(username = "cliente@banco.com")
    void consultarHistorial_sinFiltros_retorna200YPrimeraPaginaConPaginacion() throws Exception {
        HistorialTransaccionResponse item1 = HistorialTransaccionResponse.builder()
                .transaccionId(101L)
                .fechaHora(OffsetDateTime.parse("2026-10-11T15:00:00Z"))
                .tipoTransaccion("TRANSFERENCIA")
                .monto(new BigDecimal("50000.00"))
                .descripcion("Pago arriendo")
                .numeroCuentaOrigen(NUMERO_CUENTA)
                .numeroCuentaDestino("9876543210")
                .tipoMovimiento("SALIDA")
                .build();

        HistorialTransaccionResponse item2 = HistorialTransaccionResponse.builder()
                .transaccionId(100L)
                .fechaHora(OffsetDateTime.parse("2026-10-10T10:00:00Z"))
                .tipoTransaccion("CREDITO")
                .monto(new BigDecimal("100000.00"))
                .descripcion("Depósito a cuenta")
                .numeroCuentaOrigen(NUMERO_CUENTA)
                .numeroCuentaDestino(null)
                .tipoMovimiento("ENTRADA")
                .build();

        Page<HistorialTransaccionResponse> page = new PageImpl<>(List.of(item1, item2), PageRequest.of(0, 10), 2);

        given(cuentaService.consultarHistorial(eq(CLIENTE_ID), eq(NUMERO_CUENTA), isNull(), isNull(), isNull(), eq(1), eq(10)))
                .willReturn(page);

        mockMvc.perform(get("/api/v1/cuentas/{numeroCuenta}/transacciones", NUMERO_CUENTA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].transaccionId").value(101L))
                .andExpect(jsonPath("$.content[0].tipoMovimiento").value("SALIDA"))
                .andExpect(jsonPath("$.content[1].transaccionId").value(100L))
                .andExpect(jsonPath("$.content[1].tipoMovimiento").value("ENTRADA"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    @WithMockUser(username = "cliente@banco.com")
    void consultarHistorial_conRangoFechas_retorna200YTransaccionesFiltradas() throws Exception {
        LocalDate desde = LocalDate.of(2026, 10, 1);
        LocalDate hasta = LocalDate.of(2026, 10, 11);

        HistorialTransaccionResponse item = HistorialTransaccionResponse.builder()
                .transaccionId(105L)
                .fechaHora(OffsetDateTime.parse("2026-10-05T12:00:00Z"))
                .tipoTransaccion("DEBITO")
                .monto(new BigDecimal("20000.00"))
                .descripcion("Retiro de cuenta")
                .numeroCuentaOrigen(NUMERO_CUENTA)
                .tipoMovimiento("SALIDA")
                .build();

        Page<HistorialTransaccionResponse> page = new PageImpl<>(List.of(item), PageRequest.of(0, 10), 1);

        given(cuentaService.consultarHistorial(eq(CLIENTE_ID), eq(NUMERO_CUENTA), eq(desde), eq(hasta), isNull(), eq(1), eq(10)))
                .willReturn(page);

        mockMvc.perform(get("/api/v1/cuentas/{numeroCuenta}/transacciones", NUMERO_CUENTA)
                        .param("desde", "2026-10-01")
                        .param("hasta", "2026-10-11")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].transaccionId").value(105L));
    }

    @Test
    @WithMockUser(username = "cliente@banco.com")
    void consultarHistorial_conFiltroTipoTransaccion_retorna200YTransaccionesDelTipo() throws Exception {
        HistorialTransaccionResponse item = HistorialTransaccionResponse.builder()
                .transaccionId(106L)
                .fechaHora(OffsetDateTime.parse("2026-10-06T12:00:00Z"))
                .tipoTransaccion("CREDITO")
                .monto(new BigDecimal("80000.00"))
                .descripcion("Depósito nómina")
                .numeroCuentaOrigen(NUMERO_CUENTA)
                .tipoMovimiento("ENTRADA")
                .build();

        Page<HistorialTransaccionResponse> page = new PageImpl<>(List.of(item), PageRequest.of(0, 10), 1);

        given(cuentaService.consultarHistorial(eq(CLIENTE_ID), eq(NUMERO_CUENTA), isNull(), isNull(), eq("CREDITO"), eq(1), eq(10)))
                .willReturn(page);

        mockMvc.perform(get("/api/v1/cuentas/{numeroCuenta}/transacciones", NUMERO_CUENTA)
                        .param("tipoTransaccion", "CREDITO")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].tipoTransaccion").value("CREDITO"));
    }

    @Test
    @WithMockUser(username = "cliente@banco.com")
    void consultarHistorial_paginaFueraDelRango_retorna200YListaVaciaConPaginacion() throws Exception {
        Page<HistorialTransaccionResponse> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(4, 10), 15);

        given(cuentaService.consultarHistorial(eq(CLIENTE_ID), eq(NUMERO_CUENTA), isNull(), isNull(), isNull(), eq(5), eq(10)))
                .willReturn(emptyPage);

        mockMvc.perform(get("/api/v1/cuentas/{numeroCuenta}/transacciones", NUMERO_CUENTA)
                        .param("page", "5")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(15))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.number").value(4));
    }

    @Test
    @WithMockUser(username = "cliente@banco.com")
    void consultarHistorial_cuentaNoPerteneceAlCliente_retorna403ConForbidden() throws Exception {
        given(cuentaService.consultarHistorial(eq(CLIENTE_ID), eq(NUMERO_CUENTA), any(), any(), any(), anyInt(), anyInt()))
                .willThrow(new NegocioException("FORBIDDEN", "No tiene permisos para consultar esta cuenta", HttpStatus.FORBIDDEN));

        mockMvc.perform(get("/api/v1/cuentas/{numeroCuenta}/transacciones", NUMERO_CUENTA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(username = "cliente@banco.com")
    void consultarHistorial_cuentaSinTransacciones_retorna200YListaVacia() throws Exception {
        Page<HistorialTransaccionResponse> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);

        given(cuentaService.consultarHistorial(eq(CLIENTE_ID), eq(NUMERO_CUENTA), isNull(), isNull(), isNull(), eq(1), eq(10)))
                .willReturn(emptyPage);

        mockMvc.perform(get("/api/v1/cuentas/{numeroCuenta}/transacciones", NUMERO_CUENTA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    @WithMockUser(username = "cliente@banco.com")
    void consultarHistorial_rangoFechasInvalido_retorna400ConInvalidDateRange() throws Exception {
        given(cuentaService.consultarHistorial(eq(CLIENTE_ID), eq(NUMERO_CUENTA), any(), any(), any(), anyInt(), anyInt()))
                .willThrow(new NegocioException("INVALID_DATE_RANGE", "La fecha 'desde' no puede ser posterior a 'hasta'", HttpStatus.BAD_REQUEST));

        mockMvc.perform(get("/api/v1/cuentas/{numeroCuenta}/transacciones", NUMERO_CUENTA)
                        .param("desde", "2026-10-15")
                        .param("hasta", "2026-10-10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_DATE_RANGE"));
    }

    @Test
    @WithMockUser(username = "cliente@banco.com")
    void consultarHistorial_tipoTransaccionInvalido_retorna400ConInvalidTransactionType() throws Exception {
        given(cuentaService.consultarHistorial(eq(CLIENTE_ID), eq(NUMERO_CUENTA), any(), any(), eq("INEXISTENTE"), anyInt(), anyInt()))
                .willThrow(new NegocioException("INVALID_TRANSACTION_TYPE", "Tipo de transacción no válido", HttpStatus.BAD_REQUEST));

        mockMvc.perform(get("/api/v1/cuentas/{numeroCuenta}/transacciones", NUMERO_CUENTA)
                        .param("tipoTransaccion", "INEXISTENTE")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TRANSACTION_TYPE"));
    }

    @Test
    @WithMockUser(username = "cliente@banco.com")
    void consultarHistorial_cuentaBloqueada_retorna409ConAccountNotActive() throws Exception {
        given(cuentaService.consultarHistorial(eq(CLIENTE_ID), eq(NUMERO_CUENTA), any(), any(), any(), anyInt(), anyInt()))
                .willThrow(new NegocioException("ACCOUNT_NOT_ACTIVE", "La cuenta se encuentra bloqueada", HttpStatus.CONFLICT));

        mockMvc.perform(get("/api/v1/cuentas/{numeroCuenta}/transacciones", NUMERO_CUENTA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_ACTIVE"));
    }

    @Test
    @WithMockUser(username = "cliente@banco.com")
    void consultarHistorial_cuentaNoExiste_retorna404ConAccountNotFound() throws Exception {
        given(cuentaService.consultarHistorial(eq(CLIENTE_ID), eq("9999999999"), any(), any(), any(), anyInt(), anyInt()))
                .willThrow(new NegocioException("ACCOUNT_NOT_FOUND", "La cuenta consultada no existe", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/v1/cuentas/{numeroCuenta}/transacciones", "9999999999")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_FOUND"));
    }
}

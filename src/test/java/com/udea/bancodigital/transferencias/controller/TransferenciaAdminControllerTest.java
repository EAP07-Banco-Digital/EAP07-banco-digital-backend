package com.udea.bancodigital.transferencias.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.udea.bancodigital.shared.config.CustomAccessDeniedHandler;
import com.udea.bancodigital.shared.config.CustomAuthEntryPoint;
import com.udea.bancodigital.shared.config.SecurityConfig;
import com.udea.bancodigital.shared.config.TraceIdFilter;
import com.udea.bancodigital.shared.exception.NegocioException;
import com.udea.bancodigital.shared.jwt.JwtAuthenticationFilter;
import com.udea.bancodigital.shared.jwt.JwtService;
import com.udea.bancodigital.shared.jwt.RevocacionTokenService;
import com.udea.bancodigital.transferencias.dto.ConfigurarTopesRequest;
import com.udea.bancodigital.transferencias.dto.TopeTransferenciaResponse;
import com.udea.bancodigital.transferencias.service.TopeTransferenciaService;
import com.udea.bancodigital.usuarios.api.UsuarioApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransferenciaAdminController.class)
@Import({SecurityConfig.class, CustomAuthEntryPoint.class, CustomAccessDeniedHandler.class,
        JwtAuthenticationFilter.class, TraceIdFilter.class})
class TransferenciaAdminControllerTest {

    private static final String URL = "/api/v1/admin/transferencias/topes";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private TopeTransferenciaService topeTransferenciaService;

    @MockitoBean
    private UsuarioApi usuarioApi;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RevocacionTokenService revocacionTokenService;

    @Test
    @DisplayName("HU18: Administrador autenticado configura valores válidos y recibe 200 con configuración actualizada")
    void adminConfiguraValoresValidosYRecibe200() throws Exception {
        ConfigurarTopesRequest request = ConfigurarTopesRequest.builder()
                .montoMaximoPorTransferencia(new BigDecimal("8000000.00"))
                .montoMaximoAcumuladoPeriodo(new BigDecimal("20000000.00"))
                .build();

        TopeTransferenciaResponse response = TopeTransferenciaResponse.builder()
                .montoMaximoPorTransferencia(new BigDecimal("8000000.00"))
                .montoMaximoAcumuladoPeriodo(new BigDecimal("20000000.00"))
                .periodo("DIARIO")
                .esPersonalizado(false)
                .build();

        given(topeTransferenciaService.configurarTopesGlobales(any(ConfigurarTopesRequest.class)))
                .willReturn(response);

        mockMvc.perform(put(URL)
                        .with(user("admin@banco.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.montoMaximoPorTransferencia").value(8000000.00))
                .andExpect(jsonPath("$.montoMaximoAcumuladoPeriodo").value(20000000.00))
                .andExpect(jsonPath("$.periodo").value("DIARIO"))
                .andExpect(jsonPath("$.esPersonalizado").value(false));

        verify(topeTransferenciaService).configurarTopesGlobales(any(ConfigurarTopesRequest.class));
    }

    @Test
    @DisplayName("HU18: Administrador consulta la configuración global y recibe 200")
    void adminConsultaTopesGlobalesYRecibe200() throws Exception {
        TopeTransferenciaResponse response = TopeTransferenciaResponse.builder()
                .montoMaximoPorTransferencia(new BigDecimal("5000000.00"))
                .montoMaximoAcumuladoPeriodo(new BigDecimal("15000000.00"))
                .periodo("DIARIO")
                .esPersonalizado(false)
                .build();

        given(topeTransferenciaService.consultarTopesGlobales()).willReturn(response);

        mockMvc.perform(get(URL).with(user("admin@banco.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.montoMaximoPorTransferencia").value(5000000.00))
                .andExpect(jsonPath("$.montoMaximoAcumuladoPeriodo").value(15000000.00))
                .andExpect(jsonPath("$.periodo").value("DIARIO"))
                .andExpect(jsonPath("$.esPersonalizado").value(false));

        verify(topeTransferenciaService).consultarTopesGlobales();
    }

    @Test
    @DisplayName("HU18 Seguridad: Usuario cliente intenta configurar topes y recibe 403 FORBIDDEN")
    void clienteIntentaConfigurarTopesRecibe403Forbidden() throws Exception {
        ConfigurarTopesRequest request = ConfigurarTopesRequest.builder()
                .montoMaximoPorTransferencia(new BigDecimal("5000000.00"))
                .montoMaximoAcumuladoPeriodo(new BigDecimal("10000000.00"))
                .build();

        mockMvc.perform(put(URL)
                        .with(user("cliente@banco.com").roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        verifyNoInteractions(topeTransferenciaService);
    }

    @Test
    @DisplayName("HU18 Seguridad: Usuario cliente intenta consultar topes administrativos y recibe 403 FORBIDDEN")
    void clienteIntentaConsultarTopesAdminRecibe403Forbidden() throws Exception {
        mockMvc.perform(get(URL).with(user("cliente@banco.com").roles("CLIENTE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        verifyNoInteractions(topeTransferenciaService);
    }

    @Test
    @DisplayName("HU18 Seguridad: Petición sin token para configurar topes responde 401 UNAUTHORIZED")
    void sinTokenConfigurarTopesResponde401Unauthorized() throws Exception {
        ConfigurarTopesRequest request = ConfigurarTopesRequest.builder()
                .montoMaximoPorTransferencia(new BigDecimal("5000000.00"))
                .montoMaximoAcumuladoPeriodo(new BigDecimal("10000000.00"))
                .build();

        mockMvc.perform(put(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        verifyNoInteractions(topeTransferenciaService);
    }

    @Test
    @DisplayName("HU18 Seguridad: Petición sin token para consultar topes admin responde 401 UNAUTHORIZED")
    void sinTokenConsultarTopesAdminResponde401Unauthorized() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        verifyNoInteractions(topeTransferenciaService);
    }

    @Test
    @DisplayName("HU18 Validación: Monto individual igual a cero responde 400 BAD_REQUEST")
    void adminEnviaMontoIndividualCeroResponde400() throws Exception {
        String json = "{\"montoMaximoPorTransferencia\": 0, \"montoMaximoAcumuladoPeriodo\": 10000000.00}";

        mockMvc.perform(put(URL)
                        .with(user("admin@banco.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verifyNoInteractions(topeTransferenciaService);
    }

    @Test
    @DisplayName("HU18 Validación: Monto individual negativo responde 400 BAD_REQUEST")
    void adminEnviaMontoIndividualNegativoResponde400() throws Exception {
        String json = "{\"montoMaximoPorTransferencia\": -500.00, \"montoMaximoAcumuladoPeriodo\": 10000000.00}";

        mockMvc.perform(put(URL)
                        .with(user("admin@banco.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verifyNoInteractions(topeTransferenciaService);
    }

    @Test
    @DisplayName("HU18 Validación: Límite acumulado menor o igual a cero responde 400 BAD_REQUEST")
    void adminEnviaMontoAcumuladoCeroResponde400() throws Exception {
        String json = "{\"montoMaximoPorTransferencia\": 5000000.00, \"montoMaximoAcumuladoPeriodo\": 0}";

        mockMvc.perform(put(URL)
                        .with(user("admin@banco.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verifyNoInteractions(topeTransferenciaService);
    }

    @Test
    @DisplayName("HU18 Validación: Límite acumulado inferior al individual responde 400 INVALID_LIMITS")
    void adminEnviaAcumuladoMenorAIndividualResponde400() throws Exception {
        ConfigurarTopesRequest request = ConfigurarTopesRequest.builder()
                .montoMaximoPorTransferencia(new BigDecimal("10000000.00"))
                .montoMaximoAcumuladoPeriodo(new BigDecimal("5000000.00"))
                .build();

        given(topeTransferenciaService.configurarTopesGlobales(any(ConfigurarTopesRequest.class)))
                .willThrow(new NegocioException("INVALID_LIMITS",
                        "El límite acumulado no puede ser inferior al monto máximo por transferencia", HttpStatus.BAD_REQUEST));

        mockMvc.perform(put(URL)
                        .with(user("admin@banco.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_LIMITS"))
                .andExpect(jsonPath("$.message").value("El límite acumulado no puede ser inferior al monto máximo por transferencia"));
    }
}

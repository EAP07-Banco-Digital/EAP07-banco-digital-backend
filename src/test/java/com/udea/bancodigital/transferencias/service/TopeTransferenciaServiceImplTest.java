package com.udea.bancodigital.transferencias.service;

import com.udea.bancodigital.shared.exception.CodigosError;
import com.udea.bancodigital.shared.exception.NegocioException;
import com.udea.bancodigital.transferencias.dto.ConfigurarTopesRequest;
import com.udea.bancodigital.transferencias.dto.TopeTransferenciaResponse;
import com.udea.bancodigital.transferencias.entity.TopeTransferencia;
import com.udea.bancodigital.transferencias.repository.TopeTransferenciaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TopeTransferenciaServiceImplTest {

    private static final Long CLIENTE_ID = 42L;

    @Mock
    private TopeTransferenciaRepository topeTransferenciaRepository;

    @InjectMocks
    private TopeTransferenciaServiceImpl topeTransferenciaService;

    // ==================== HU17: Consultar Topes ====================

    @Test
    @DisplayName("ESCENARIO 1 (HU17): Cliente con topes personalizados retorna dichos valores y esPersonalizado=true")
    void consultarTopes_conTopesPersonalizados_retornaTopesPersonalizados() {
        TopeTransferencia topePersonalizado = TopeTransferencia.builder()
                .id(1L)
                .clienteId(CLIENTE_ID)
                .montoMaximoIndividual(new BigDecimal("3000000.00"))
                .montoMaximoAcumulado(new BigDecimal("8000000.00"))
                .periodo("DIARIO")
                .fechaActualizacion(OffsetDateTime.now())
                .build();

        given(topeTransferenciaRepository.findByClienteId(CLIENTE_ID))
                .willReturn(Optional.of(topePersonalizado));

        TopeTransferenciaResponse response = topeTransferenciaService.consultarTopesParaCliente(CLIENTE_ID);

        assertThat(response).isNotNull();
        assertThat(response.getMontoMaximoPorTransferencia()).isEqualByComparingTo("3000000.00");
        assertThat(response.getMontoMaximoAcumuladoPeriodo()).isEqualByComparingTo("8000000.00");
        assertThat(response.getPeriodo()).isEqualTo("DIARIO");
        assertThat(response.isEsPersonalizado()).isTrue();

        verify(topeTransferenciaRepository).findByClienteId(CLIENTE_ID);
    }

    @Test
    @DisplayName("ESCENARIO 2 (HU17): Cliente sin topes personalizados retorna topes predeterminados del banco y esPersonalizado=false")
    void consultarTopes_sinTopesPersonalizados_retornaPredeterminadosDelBanco() {
        TopeTransferencia topeGlobal = TopeTransferencia.builder()
                .id(99L)
                .clienteId(null)
                .montoMaximoIndividual(new BigDecimal("5000000.00"))
                .montoMaximoAcumulado(new BigDecimal("15000000.00"))
                .periodo("DIARIO")
                .fechaActualizacion(OffsetDateTime.now())
                .build();

        given(topeTransferenciaRepository.findByClienteId(CLIENTE_ID))
                .willReturn(Optional.empty());
        given(topeTransferenciaRepository.findByClienteIdIsNull())
                .willReturn(Optional.of(topeGlobal));

        TopeTransferenciaResponse response = topeTransferenciaService.consultarTopesParaCliente(CLIENTE_ID);

        assertThat(response).isNotNull();
        assertThat(response.getMontoMaximoPorTransferencia()).isEqualByComparingTo("5000000.00");
        assertThat(response.getMontoMaximoAcumuladoPeriodo()).isEqualByComparingTo("15000000.00");
        assertThat(response.getPeriodo()).isEqualTo("DIARIO");
        assertThat(response.isEsPersonalizado()).isFalse();

        verify(topeTransferenciaRepository).findByClienteId(CLIENTE_ID);
        verify(topeTransferenciaRepository).findByClienteIdIsNull();
    }

    @Test
    @DisplayName("Sin topes configurados en BD (ni personalizados ni globales) lanza NegocioException SERVICE_UNAVAILABLE")
    void consultarTopes_sinTopesEnBD_lanzaNegocioException() {
        given(topeTransferenciaRepository.findByClienteId(CLIENTE_ID))
                .willReturn(Optional.empty());
        given(topeTransferenciaRepository.findByClienteIdIsNull())
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> topeTransferenciaService.consultarTopesParaCliente(CLIENTE_ID))
                .isInstanceOf(NegocioException.class)
                .satisfies(ex -> {
                    NegocioException ne = (NegocioException) ex;
                    assertThat(ne.getErrorCode()).isEqualTo("TOPES_NO_CONFIGURADOS");
                    assertThat(ne.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
    }

    @Test
    @DisplayName("ESCENARIO 3 (HU17): Sin clienteId (no autenticado) lanza NegocioException UNAUTHORIZED")
    void consultarTopes_clienteIdNulo_lanzaNegocioExceptionUnauthorized() {
        assertThatThrownBy(() -> topeTransferenciaService.consultarTopesParaCliente(null))
                .isInstanceOf(NegocioException.class)
                .satisfies(ex -> {
                    NegocioException ne = (NegocioException) ex;
                    assertThat(ne.getErrorCode()).isEqualTo(CodigosError.UNAUTHORIZED);
                    assertThat(ne.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }

    // ==================== HU18: Configurar Topes ====================

    @Test
    @DisplayName("HU18: Administrador configura valores válidos por primera vez (crea registro global)")
    void configurarTopesGlobales_primeraVez_creaYPersisteRegistroGlobal() {
        ConfigurarTopesRequest request = ConfigurarTopesRequest.builder()
                .montoMaximoPorTransferencia(new BigDecimal("5000000.00"))
                .montoMaximoAcumuladoPeriodo(new BigDecimal("15000000.00"))
                .build();

        given(topeTransferenciaRepository.findByClienteIdIsNull())
                .willReturn(Optional.empty());

        given(topeTransferenciaRepository.save(any(TopeTransferencia.class)))
                .willAnswer(inv -> {
                    TopeTransferencia t = inv.getArgument(0);
                    t.setId(10L);
                    return t;
                });

        TopeTransferenciaResponse response = topeTransferenciaService.configurarTopesGlobales(request);

        assertThat(response).isNotNull();
        assertThat(response.getMontoMaximoPorTransferencia()).isEqualByComparingTo("5000000.00");
        assertThat(response.getMontoMaximoAcumuladoPeriodo()).isEqualByComparingTo("15000000.00");
        assertThat(response.getPeriodo()).isEqualTo("DIARIO");
        assertThat(response.isEsPersonalizado()).isFalse();

        ArgumentCaptor<TopeTransferencia> captor = ArgumentCaptor.forClass(TopeTransferencia.class);
        verify(topeTransferenciaRepository).save(captor.capture());
        TopeTransferencia guardado = captor.getValue();
        assertThat(guardado.getClienteId()).isNull();
        assertThat(guardado.getMontoMaximoIndividual()).isEqualByComparingTo("5000000.00");
        assertThat(guardado.getMontoMaximoAcumulado()).isEqualByComparingTo("15000000.00");
        assertThat(guardado.getPeriodo()).isEqualTo("DIARIO");
        assertThat(guardado.getFechaActualizacion()).isNotNull();
    }

    @Test
    @DisplayName("HU18: Administrador actualiza valores válidos sobre registro global existente (no duplica)")
    void configurarTopesGlobales_actualizacion_modificaRegistroExistenteSinDuplicar() {
        ConfigurarTopesRequest request = ConfigurarTopesRequest.builder()
                .montoMaximoPorTransferencia(new BigDecimal("8000000.00"))
                .montoMaximoAcumuladoPeriodo(new BigDecimal("20000000.00"))
                .build();

        TopeTransferencia existente = TopeTransferencia.builder()
                .id(5L)
                .clienteId(null)
                .montoMaximoIndividual(new BigDecimal("5000000.00"))
                .montoMaximoAcumulado(new BigDecimal("10000000.00"))
                .periodo("DIARIO")
                .fechaActualizacion(OffsetDateTime.now().minusDays(1))
                .build();

        given(topeTransferenciaRepository.findByClienteIdIsNull())
                .willReturn(Optional.of(existente));

        given(topeTransferenciaRepository.save(any(TopeTransferencia.class)))
                .willAnswer(inv -> inv.getArgument(0));

        TopeTransferenciaResponse response = topeTransferenciaService.configurarTopesGlobales(request);

        assertThat(response).isNotNull();
        assertThat(response.getMontoMaximoPorTransferencia()).isEqualByComparingTo("8000000.00");
        assertThat(response.getMontoMaximoAcumuladoPeriodo()).isEqualByComparingTo("20000000.00");
        assertThat(response.getPeriodo()).isEqualTo("DIARIO");
        assertThat(response.isEsPersonalizado()).isFalse();

        verify(topeTransferenciaRepository).save(existente);
        assertThat(existente.getId()).isEqualTo(5L);
        assertThat(existente.getClienteId()).isNull();
    }

    @Test
    @DisplayName("HU18 Validación: Monto individual igual a cero lanza BAD_REQUEST")
    void configurarTopesGlobales_montoIndividualCero_lanzaNegocioException() {
        ConfigurarTopesRequest request = ConfigurarTopesRequest.builder()
                .montoMaximoPorTransferencia(BigDecimal.ZERO)
                .montoMaximoAcumuladoPeriodo(new BigDecimal("10000000.00"))
                .build();

        assertThatThrownBy(() -> topeTransferenciaService.configurarTopesGlobales(request))
                .isInstanceOf(NegocioException.class)
                .satisfies(ex -> {
                    NegocioException ne = (NegocioException) ex;
                    assertThat(ne.getErrorCode()).isEqualTo("INVALID_LIMITS");
                    assertThat(ne.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    @DisplayName("HU18 Validación: Monto individual negativo lanza BAD_REQUEST")
    void configurarTopesGlobales_montoIndividualNegativo_lanzaNegocioException() {
        ConfigurarTopesRequest request = ConfigurarTopesRequest.builder()
                .montoMaximoPorTransferencia(new BigDecimal("-1000.00"))
                .montoMaximoAcumuladoPeriodo(new BigDecimal("10000000.00"))
                .build();

        assertThatThrownBy(() -> topeTransferenciaService.configurarTopesGlobales(request))
                .isInstanceOf(NegocioException.class)
                .satisfies(ex -> {
                    NegocioException ne = (NegocioException) ex;
                    assertThat(ne.getErrorCode()).isEqualTo("INVALID_LIMITS");
                    assertThat(ne.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    @DisplayName("HU18 Validación: Límite acumulado igual a cero lanza BAD_REQUEST")
    void configurarTopesGlobales_montoAcumuladoCero_lanzaNegocioException() {
        ConfigurarTopesRequest request = ConfigurarTopesRequest.builder()
                .montoMaximoPorTransferencia(new BigDecimal("5000000.00"))
                .montoMaximoAcumuladoPeriodo(BigDecimal.ZERO)
                .build();

        assertThatThrownBy(() -> topeTransferenciaService.configurarTopesGlobales(request))
                .isInstanceOf(NegocioException.class)
                .satisfies(ex -> {
                    NegocioException ne = (NegocioException) ex;
                    assertThat(ne.getErrorCode()).isEqualTo("INVALID_LIMITS");
                    assertThat(ne.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    @DisplayName("HU18 Validación: Límite acumulado negativo lanza BAD_REQUEST")
    void configurarTopesGlobales_montoAcumuladoNegativo_lanzaNegocioException() {
        ConfigurarTopesRequest request = ConfigurarTopesRequest.builder()
                .montoMaximoPorTransferencia(new BigDecimal("5000000.00"))
                .montoMaximoAcumuladoPeriodo(new BigDecimal("-500.00"))
                .build();

        assertThatThrownBy(() -> topeTransferenciaService.configurarTopesGlobales(request))
                .isInstanceOf(NegocioException.class)
                .satisfies(ex -> {
                    NegocioException ne = (NegocioException) ex;
                    assertThat(ne.getErrorCode()).isEqualTo("INVALID_LIMITS");
                    assertThat(ne.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    @DisplayName("HU18 Validación: Límite acumulado inferior al individual lanza BAD_REQUEST")
    void configurarTopesGlobales_acumuladoMenorAIndividual_lanzaNegocioException() {
        ConfigurarTopesRequest request = ConfigurarTopesRequest.builder()
                .montoMaximoPorTransferencia(new BigDecimal("10000000.00"))
                .montoMaximoAcumuladoPeriodo(new BigDecimal("5000000.00"))
                .build();

        assertThatThrownBy(() -> topeTransferenciaService.configurarTopesGlobales(request))
                .isInstanceOf(NegocioException.class)
                .satisfies(ex -> {
                    NegocioException ne = (NegocioException) ex;
                    assertThat(ne.getErrorCode()).isEqualTo("INVALID_LIMITS");
                    assertThat(ne.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    @DisplayName("HU18: Consultar topes globales cuando están configurados devuelve 200 con la configuración")
    void consultarTopesGlobales_existente_retornaConfiguracion() {
        TopeTransferencia global = TopeTransferencia.builder()
                .id(1L)
                .clienteId(null)
                .montoMaximoIndividual(new BigDecimal("5000000.00"))
                .montoMaximoAcumulado(new BigDecimal("15000000.00"))
                .periodo("DIARIO")
                .fechaActualizacion(OffsetDateTime.now())
                .build();

        given(topeTransferenciaRepository.findByClienteIdIsNull())
                .willReturn(Optional.of(global));

        TopeTransferenciaResponse response = topeTransferenciaService.consultarTopesGlobales();

        assertThat(response).isNotNull();
        assertThat(response.getMontoMaximoPorTransferencia()).isEqualByComparingTo("5000000.00");
        assertThat(response.getMontoMaximoAcumuladoPeriodo()).isEqualByComparingTo("15000000.00");
        assertThat(response.getPeriodo()).isEqualTo("DIARIO");
        assertThat(response.isEsPersonalizado()).isFalse();
    }

    @Test
    @DisplayName("HU18: Consultar topes globales cuando no existen lanza SERVICE_UNAVAILABLE")
    void consultarTopesGlobales_inexistente_lanzaServiceUnavailable() {
        given(topeTransferenciaRepository.findByClienteIdIsNull())
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> topeTransferenciaService.consultarTopesGlobales())
                .isInstanceOf(NegocioException.class)
                .satisfies(ex -> {
                    NegocioException ne = (NegocioException) ex;
                    assertThat(ne.getErrorCode()).isEqualTo("TOPES_NO_CONFIGURADOS");
                    assertThat(ne.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
    }
}

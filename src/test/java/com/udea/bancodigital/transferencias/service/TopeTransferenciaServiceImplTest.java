package com.udea.bancodigital.transferencias.service;

import com.udea.bancodigital.shared.exception.CodigosError;
import com.udea.bancodigital.shared.exception.NegocioException;
import com.udea.bancodigital.transferencias.dto.TopeTransferenciaResponse;
import com.udea.bancodigital.transferencias.entity.TopeTransferencia;
import com.udea.bancodigital.transferencias.repository.TopeTransferenciaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TopeTransferenciaServiceImplTest {

    private static final Long CLIENTE_ID = 42L;

    @Mock
    private TopeTransferenciaRepository topeTransferenciaRepository;

    @InjectMocks
    private TopeTransferenciaServiceImpl topeTransferenciaService;

    @Test
    @DisplayName("ESCENARIO 1: Cliente con topes personalizados retorna dichos valores y esPersonalizado=true")
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
    @DisplayName("ESCENARIO 2: Cliente sin topes personalizados retorna topes predeterminados del banco y esPersonalizado=false")
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
    @DisplayName("ESCENARIO 3: Sin clienteId (no autenticado) lanza NegocioException UNAUTHORIZED")
    void consultarTopes_clienteIdNulo_lanzaNegocioExceptionUnauthorized() {
        assertThatThrownBy(() -> topeTransferenciaService.consultarTopesParaCliente(null))
                .isInstanceOf(NegocioException.class)
                .satisfies(ex -> {
                    NegocioException ne = (NegocioException) ex;
                    assertThat(ne.getErrorCode()).isEqualTo(CodigosError.UNAUTHORIZED);
                    assertThat(ne.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }
}

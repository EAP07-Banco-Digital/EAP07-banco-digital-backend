package com.udea.bancodigital.transferencias.api;

import com.udea.bancodigital.shared.exception.NegocioException;
import com.udea.bancodigital.transferencias.entity.TipoTransaccion;
import com.udea.bancodigital.transferencias.entity.Transaccion;
import com.udea.bancodigital.transferencias.repository.TipoTransaccionRepository;
import com.udea.bancodigital.transferencias.repository.TransaccionRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransaccionApiImplTest {

    @Mock
    private TransaccionRepository transaccionRepository;

    @Mock
    private TipoTransaccionRepository tipoTransaccionRepository;

    @InjectMocks
    private TransaccionApiImpl transaccionApi;

    private TipoTransaccion tipoTransaccion(Long id, String nombre) {
        return new TipoTransaccion(id, nombre);
    }

    private Transaccion transaccion(Long id, Long origenId, Long destinoId, TipoTransaccion tipo,
                                   BigDecimal monto, String descripcion, OffsetDateTime fecha) {
        return Transaccion.builder()
                .id(id)
                .cuentaOrigenId(origenId)
                .cuentaDestinoId(destinoId)
                .tipoTransaccion(tipo)
                .monto(monto)
                .estado("EXITOSA")
                .descripcion(descripcion)
                .fechaHora(fecha)
                .build();
    }

    // ------------------------------------------------------------- registrarTransaccion

    @Test
    void registrarTransaccion_Exitoso() {
        TipoTransaccion tipo = tipoTransaccion(1L, "TRANSFERENCIA");
        given(tipoTransaccionRepository.findByNombreIgnoreCase("TRANSFERENCIA"))
                .willReturn(Optional.of(tipo));

        OffsetDateTime ahora = OffsetDateTime.now();
        Transaccion guardada = transaccion(100L, 1L, 2L, tipo, new BigDecimal("50000.00"), "Prueba", ahora);
        given(transaccionRepository.save(any(Transaccion.class))).willReturn(guardada);

        TransaccionApi.TransaccionInfo resultado = transaccionApi.registrarTransaccion(
                1L, 2L, "TRANSFERENCIA", new BigDecimal("50000.00"), "Prueba"
        );

        assertThat(resultado).isNotNull();
        assertThat(resultado.transaccionId()).isEqualTo(100L);
        assertThat(resultado.fechaHora()).isEqualTo(ahora);

        ArgumentCaptor<Transaccion> captor = ArgumentCaptor.forClass(Transaccion.class);
        verify(transaccionRepository).save(captor.capture());
        Transaccion enviada = captor.getValue();
        assertThat(enviada.getCuentaOrigenId()).isEqualTo(1L);
        assertThat(enviada.getCuentaDestinoId()).isEqualTo(2L);
        assertThat(enviada.getTipoTransaccion()).isEqualTo(tipo);
        assertThat(enviada.getMonto()).isEqualByComparingTo("50000.00");
        assertThat(enviada.getEstado()).isEqualTo("EXITOSA");
        assertThat(enviada.getDescripcion()).isEqualTo("Prueba");
        assertThat(enviada.getFechaHora()).isNotNull();
    }

    @Test
    void registrarTransaccion_TipoNoConfigurado_Lanza500() {
        given(tipoTransaccionRepository.findByNombreIgnoreCase("NO_EXISTE"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> transaccionApi.registrarTransaccion(
                1L, 2L, "NO_EXISTE", new BigDecimal("50000.00"), "Prueba"
        )).isInstanceOfSatisfying(NegocioException.class, ex -> {
            assertThat(ex.getErrorCode()).isEqualTo("INTERNAL_ERROR");
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(ex.getMessage()).contains("NO_EXISTE");
        });

        verify(transaccionRepository, never()).save(any());
    }

    // ------------------------------------------------------------- consultarHistorial

    @Test
    void consultarHistorial_ConTipoValido_FiltraYRetornaPagina() {
        TipoTransaccion tipo = tipoTransaccion(2L, "CONSIGNACION");
        given(tipoTransaccionRepository.findByNombreIgnoreCase("CONSIGNACION"))
                .willReturn(Optional.of(tipo));

        OffsetDateTime fecha = OffsetDateTime.parse("2026-10-18T10:00:00Z");
        Transaccion tx = transaccion(10L, 1L, 2L, tipo, new BigDecimal("30000.00"), "Abono", fecha);
        Page<Transaccion> paginaMock = new PageImpl<>(List.of(tx));

        given(transaccionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(paginaMock);

        Pageable pageable = PageRequest.of(0, 10);
        Page<TransaccionApi.TransaccionDetalle> resultado = transaccionApi.consultarHistorial(
                1L, fecha.minusDays(1), fecha.plusDays(1), "  CONSIGNACION  ", pageable
        );

        assertThat(resultado).isNotNull();
        assertThat(resultado.getContent()).hasSize(1);

        TransaccionApi.TransaccionDetalle detalle = resultado.getContent().get(0);
        assertThat(detalle.id()).isEqualTo(10L);
        assertThat(detalle.cuentaOrigenId()).isEqualTo(1L);
        assertThat(detalle.cuentaDestinoId()).isEqualTo(2L);
        assertThat(detalle.tipoTransaccion()).isEqualTo("CONSIGNACION");
        assertThat(detalle.monto()).isEqualByComparingTo("30000.00");
        assertThat(detalle.fechaHora()).isEqualTo(fecha);
        assertThat(detalle.descripcion()).isEqualTo("Abono");

        verify(tipoTransaccionRepository).findByNombreIgnoreCase("CONSIGNACION");
        verify(transaccionRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void consultarHistorial_ConTipoInvalido_Lanza400() {
        given(tipoTransaccionRepository.findByNombreIgnoreCase("INVALIDO"))
                .willReturn(Optional.empty());

        Pageable pageable = PageRequest.of(0, 10);

        assertThatThrownBy(() -> transaccionApi.consultarHistorial(
                1L, null, null, "INVALIDO", pageable
        )).isInstanceOfSatisfying(NegocioException.class, ex -> {
            assertThat(ex.getErrorCode()).isEqualTo("INVALID_TRANSACTION_TYPE");
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(ex.getMessage()).contains("INVALIDO");
        });

        verify(transaccionRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void consultarHistorial_SinTipoTransaccionNull_NoConsultaTipoRepository() {
        Page<Transaccion> paginaMock = new PageImpl<>(List.of());
        given(transaccionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(paginaMock);

        Pageable pageable = PageRequest.of(0, 10);
        Page<TransaccionApi.TransaccionDetalle> resultado = transaccionApi.consultarHistorial(
                1L, null, null, null, pageable
        );

        assertThat(resultado).isNotNull();
        verify(tipoTransaccionRepository, never()).findByNombreIgnoreCase(anyString());
        verify(transaccionRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void consultarHistorial_ConTipoEnBlanco_NoConsultaTipoRepository() {
        Page<Transaccion> paginaMock = new PageImpl<>(List.of());
        given(transaccionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(paginaMock);

        Pageable pageable = PageRequest.of(0, 10);
        Page<TransaccionApi.TransaccionDetalle> resultado = transaccionApi.consultarHistorial(
                1L, null, null, "    ", pageable
        );

        assertThat(resultado).isNotNull();
        verify(tipoTransaccionRepository, never()).findByNombreIgnoreCase(anyString());
        verify(transaccionRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    // ------------------------------------------------------------- Cobertura Specification Lambda

    @Test
    @SuppressWarnings("unchecked")
    void consultarHistorial_Specification_EjecucionConTodosLosFiltros() {
        TipoTransaccion tipo = tipoTransaccion(5L, "RETIRO");
        given(tipoTransaccionRepository.findByNombreIgnoreCase("RETIRO"))
                .willReturn(Optional.of(tipo));

        Page<Transaccion> paginaVacia = new PageImpl<>(List.of());
        given(transaccionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(paginaVacia);

        OffsetDateTime desde = OffsetDateTime.parse("2026-10-01T00:00:00Z");
        OffsetDateTime hasta = OffsetDateTime.parse("2026-10-18T23:59:59Z");
        Pageable pageable = PageRequest.of(0, 10);

        transaccionApi.consultarHistorial(1L, desde, hasta, "RETIRO", pageable);

        ArgumentCaptor<Specification<Transaccion>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(transaccionRepository).findAll(specCaptor.capture(), any(Pageable.class));

        Specification<Transaccion> spec = specCaptor.getValue();
        assertThat(spec).isNotNull();

        Root<Transaccion> root = mock(Root.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, org.mockito.Answers.RETURNS_MOCKS);

        Predicate resultadoPred = spec.toPredicate(root, query, cb);
        assertThat(resultadoPred).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void consultarHistorial_Specification_EjecucionConFiltrosNulos() {
        Page<Transaccion> paginaVacia = new PageImpl<>(List.of());
        given(transaccionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(paginaVacia);

        Pageable pageable = PageRequest.of(0, 10);
        transaccionApi.consultarHistorial(1L, null, null, null, pageable);

        ArgumentCaptor<Specification<Transaccion>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(transaccionRepository).findAll(specCaptor.capture(), any(Pageable.class));

        Specification<Transaccion> spec = specCaptor.getValue();
        assertThat(spec).isNotNull();

        Root<Transaccion> root = mock(Root.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, org.mockito.Answers.RETURNS_MOCKS);

        Predicate resultadoPred = spec.toPredicate(root, query, cb);
        assertThat(resultadoPred).isNotNull();
    }
}

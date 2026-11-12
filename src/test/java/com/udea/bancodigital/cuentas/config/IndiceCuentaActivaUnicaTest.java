package com.udea.bancodigital.cuentas.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * DEF-HU5-02: el indice se crea con el id real del estado ACTIVA, y si algo
 * falla la aplicacion sigue arrancando.
 */
@ExtendWith(MockitoExtension.class)
class IndiceCuentaActivaUnicaTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private IndiceCuentaActivaUnica indice;

    @Test
    void creaElIndiceParcialConElIdDelEstadoActiva() {
        given(jdbcTemplate.queryForList(anyString(), eq(Long.class))).willReturn(List.of(5L));

        indice.run(null);

        verify(jdbcTemplate).execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_cuentas_cliente_tipo_activa"
                + " ON cuentas (cliente_id, tipo_cuenta_id) WHERE estado_id = 5");
    }

    @Test
    void sinUnUnicoEstadoActivaNoCreaNada() {
        given(jdbcTemplate.queryForList(anyString(), eq(Long.class))).willReturn(List.of());

        assertThat(indice.asegurarIndice()).isFalse();
        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    void siLaBaseRechazaElIndiceLoRegistraYNoDetieneElArranque() {
        given(jdbcTemplate.queryForList(anyString(), eq(Long.class))).willReturn(List.of(5L));
        willThrow(new DataIntegrityViolationException("could not create unique index"))
                .given(jdbcTemplate).execute(anyString());

        assertThat(indice.asegurarIndice()).isFalse();
    }
}

package com.udea.bancodigital.shared.jwt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.io.InputStream;
import java.time.Instant;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PurgaTokensRevocadosTaskTest {

    @Mock
    private RevocacionTokenService revocacionTokenService;

    @InjectMocks
    private PurgaTokensRevocadosTask tarea;

    @Test
    void laPurgaUsaElInstanteActualComoLimite() {
        Instant antes = Instant.now();

        tarea.purgar();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(revocacionTokenService).purgarExpirados(captor.capture());
        assertThat(captor.getValue()).isBetween(antes, Instant.now());
    }

    @Test
    void laTareaEstaProgramadaConElCronDeLaConfiguracion() throws Exception {
        Scheduled programacion = PurgaTokensRevocadosTask.class.getMethod("purgar").getAnnotation(Scheduled.class);

        assertThat(programacion).isNotNull();
        assertThat(programacion.cron()).isEqualTo("${jwt.revocados.purga-cron}");
    }

    @Test
    void elCronPorDefectoEsValidoYCorreCadaHora() throws Exception {
        Properties propiedades = new Properties();
        try (InputStream entrada = getClass().getResourceAsStream("/application.properties")) {
            propiedades.load(entrada);
        }
        // ${JWT_REVOCADOS_PURGA_CRON:0 0 * * * *} -> se toma el valor por defecto.
        String valor = propiedades.getProperty("jwt.revocados.purga-cron");
        String cronPorDefecto = valor.substring(valor.indexOf(':') + 1, valor.length() - 1);

        CronExpression cron = CronExpression.parse(cronPorDefecto);
        java.time.LocalDateTime base = java.time.LocalDateTime.of(2026, 10, 6, 18, 15);
        assertThat(cron.next(base)).isEqualTo(java.time.LocalDateTime.of(2026, 10, 6, 19, 0));
    }
}

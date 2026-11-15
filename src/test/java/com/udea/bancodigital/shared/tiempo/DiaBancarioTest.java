package com.udea.bancodigital.shared.tiempo;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class DiaBancarioTest {

    private static final LocalDate DIA = LocalDate.of(2026, 11, 12);

    @Test
    void elDiaEmpiezaALaMedianocheDeColombia() {
        assertThat(DiaBancario.inicio(DIA).toInstant())
                .isEqualTo(OffsetDateTime.parse("2026-11-12T05:00:00Z").toInstant());
    }

    @Test
    void elFinExclusivoEsLaMedianocheDelDiaSiguiente() {
        assertThat(DiaBancario.finExclusivo(DIA).toInstant())
                .isEqualTo(OffsetDateTime.parse("2026-11-13T05:00:00Z").toInstant());
    }

    @Test
    void elUltimoInstanteQuedaJustoAntesDelDiaSiguiente() {
        assertThat(DiaBancario.ultimoInstante(DIA).toInstant())
                .isEqualTo(OffsetDateTime.parse("2026-11-13T04:59:59.999999Z").toInstant());
    }

    @Test
    void unMovimientoALas8DeLaNocheEnColombiaPerteneceAEseDiaAunqueEnUtcSeaElSiguiente() {
        // 2026-11-12 20:00 en Bogota = 2026-11-13 01:00 UTC
        OffsetDateTime nocheEnColombia = OffsetDateTime.parse("2026-11-13T01:00:00Z");

        assertThat(nocheEnColombia).isAfterOrEqualTo(DiaBancario.inicio(DIA));
        assertThat(nocheEnColombia).isBefore(DiaBancario.finExclusivo(DIA));
    }

    @Test
    void enZonaDelBancoConvierteUtcALaZonaDeColombiaSinCambiarElInstante() {
        OffsetDateTime utc = OffsetDateTime.parse("2026-10-24T21:09:32.076219Z");

        OffsetDateTime enBogota = DiaBancario.enZonaDelBanco(utc);

        assertThat(enBogota).isEqualTo(OffsetDateTime.parse("2026-10-24T16:09:32.076219-05:00"));
        assertThat(enBogota.toInstant()).isEqualTo(utc.toInstant());
    }

    @Test
    void enZonaDelBancoDejaIgualUnaFechaQueYaEstaEnLaZonaDelBanco() {
        OffsetDateTime fecha = OffsetDateTime.parse("2026-10-24T16:09:32.076219-05:00");

        assertThat(DiaBancario.enZonaDelBanco(fecha)).isEqualTo(fecha);
    }

    @Test
    void enZonaDelBancoTruncaLosNanosegundosAMicrosegundos() {
        OffsetDateTime conNanos = OffsetDateTime.parse("2026-10-24T16:09:32.0762189-05:00");

        assertThat(DiaBancario.enZonaDelBanco(conNanos))
                .isEqualTo(OffsetDateTime.parse("2026-10-24T16:09:32.076218-05:00"));
    }

    @Test
    void enZonaDelBancoConNuloDevuelveNulo() {
        assertThat(DiaBancario.enZonaDelBanco(null)).isNull();
    }

    @Test
    void ahoraEstaEnLaZonaDelBancoYConPrecisionDeMicrosegundos() {
        OffsetDateTime ahora = DiaBancario.ahora();

        assertThat(ahora.getOffset()).isEqualTo(ZoneOffset.of("-05:00"));
        assertThat(ahora.getNano() % 1000).isZero();
    }
}

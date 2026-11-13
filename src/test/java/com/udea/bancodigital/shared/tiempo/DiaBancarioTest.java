package com.udea.bancodigital.shared.tiempo;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;

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
}

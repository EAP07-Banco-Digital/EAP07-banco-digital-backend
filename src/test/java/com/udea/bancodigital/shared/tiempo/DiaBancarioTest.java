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

    @Test
    void casoObligatorio_movimientoNocheColombia_incluidoEnDiaCorrectoYExcluidoEnSiguiente() {
        // Movimiento: 2026-10-23T01:00:00Z = 2026-10-22 20:00:00 en Colombia
        OffsetDateTime movimiento = OffsetDateTime.parse("2026-10-23T01:00:00Z");
        LocalDate dia22 = LocalDate.of(2026, 10, 22);
        LocalDate dia23 = LocalDate.of(2026, 10, 23);

        // Consulta desde=2026-10-22&hasta=2026-10-22 -> debe incluir
        OffsetDateTime desde22 = DiaBancario.inicio(dia22);
        OffsetDateTime hasta22 = DiaBancario.finExclusivo(dia22);
        boolean incluidoEn22 = !movimiento.isBefore(desde22) && movimiento.isBefore(hasta22);
        assertThat(incluidoEn22).isTrue();

        // Consulta desde=2026-10-23&hasta=2026-10-23 -> no debe incluir
        OffsetDateTime desde23 = DiaBancario.inicio(dia23);
        OffsetDateTime hasta23 = DiaBancario.finExclusivo(dia23);
        boolean incluidoEn23 = !movimiento.isBefore(desde23) && movimiento.isBefore(hasta23);
        assertThat(incluidoEn23).isFalse();
    }

    @Test
    void movimientoAlInicioDelDia_entraEnEseDiaYNoEnElAnterior() {
        LocalDate dia = LocalDate.of(2026, 10, 22);
        LocalDate diaAnterior = LocalDate.of(2026, 10, 21);
        OffsetDateTime inicioExacto = DiaBancario.inicio(dia); // 2026-10-22T00:00:00-05:00 = 05:00:00Z

        // Entra en el dia 22
        assertThat(inicioExacto).isAfterOrEqualTo(DiaBancario.inicio(dia));
        assertThat(inicioExacto).isBefore(DiaBancario.finExclusivo(dia));

        // No entra en el dia 21
        assertThat(inicioExacto).isAfterOrEqualTo(DiaBancario.finExclusivo(diaAnterior));
    }

    @Test
    void movimientoAlFinalDelDia_entraEnEseDiaYNoEnElSiguiente() {
        LocalDate dia = LocalDate.of(2026, 10, 22);
        LocalDate diaSiguiente = LocalDate.of(2026, 10, 23);
        OffsetDateTime finalDelDia = DiaBancario.ultimoInstante(dia); // 2026-10-22T23:59:59.999999-05:00

        // Entra en el dia 22
        assertThat(finalDelDia).isAfterOrEqualTo(DiaBancario.inicio(dia));
        assertThat(finalDelDia).isBefore(DiaBancario.finExclusivo(dia));

        // No entra en el dia 23
        assertThat(finalDelDia).isBefore(DiaBancario.inicio(diaSiguiente));
    }

    @Test
    void consultaFechasConsecutivas_cubreTodoElIntervalo() {
        LocalDate desde = LocalDate.of(2026, 10, 22);
        LocalDate hasta = LocalDate.of(2026, 10, 23);

        OffsetDateTime inicioIntervalo = DiaBancario.inicio(desde);
        OffsetDateTime finIntervalo = DiaBancario.finExclusivo(hasta);

        // Comprueba que cubre desde las 00:00:00 del 22 hasta las 00:00:00 del 24
        assertThat(inicioIntervalo).isEqualTo(OffsetDateTime.parse("2026-10-22T00:00:00-05:00"));
        assertThat(finIntervalo).isEqualTo(OffsetDateTime.parse("2026-10-24T00:00:00-05:00"));

        // Movimiento a las 23:30 del 23 entra en el rango
        OffsetDateTime noche23 = OffsetDateTime.parse("2026-10-23T23:30:00-05:00");
        assertThat(noche23).isAfterOrEqualTo(inicioIntervalo);
        assertThat(noche23).isBefore(finIntervalo);
    }
}

package com.udea.bancodigital.shared.tiempo;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Limites de un dia calendario para los filtros por fecha (historial, reportes).
 *
 * <p>Los clientes piden fechas de Colombia: "hoy" es el dia en Bogota, no en UTC.
 * Con dias UTC, todo lo hecho despues de las 7 p. m. caia en el dia siguiente y el
 * reporte de "hoy" salia incompleto. Las fechas guardadas siguen siendo instantes
 * (timestamptz); solo cambia donde empieza y termina cada dia.</p>
 */
public final class DiaBancario {

    /** Zona horaria del banco. Colombia no tiene horario de verano: siempre UTC-5. */
    public static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private DiaBancario() {
    }

    /** Primer instante del dia en la zona del banco. */
    public static OffsetDateTime inicio(LocalDate dia) {
        return dia.atStartOfDay(ZONA).toOffsetDateTime();
    }

    /** Primer instante del dia siguiente: limite exclusivo del dia. */
    public static OffsetDateTime finExclusivo(LocalDate dia) {
        return inicio(dia.plusDays(1));
    }

    /**
     * Ultimo instante del dia, para los filtros que comparan con un limite inclusivo.
     * Es el ultimo microsegundo, la precision de PostgreSQL: con nanosegundos, la base
     * redondearia 23:59:59.999999999 a la medianoche del dia siguiente y la incluiria.
     */
    public static OffsetDateTime ultimoInstante(LocalDate dia) {
        return finExclusivo(dia).minus(1, ChronoUnit.MICROS);
    }
}

package com.udea.bancodigital.cuentas.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * DEF-HU5-02: garantiza en la base que un cliente no tenga dos cuentas ACTIVA
 * del mismo tipo, aunque dos aperturas pasen a la vez la validacion del servicio.
 * <p>
 * Es un indice unico <b>parcial</b>: solo cuenta las filas en estado ACTIVA, asi
 * que una cuenta INACTIVA o BLOQUEADA no impide abrir otra del mismo tipo.
 * <p>
 * Se crea al arrancar porque Hibernate ({@code ddl-auto=update}) no sabe declarar
 * indices parciales, y el predicado necesita el id real del estado ACTIVA (un
 * indice no admite subconsultas). {@code IF NOT EXISTS} lo hace idempotente. Si
 * no se puede crear (por ejemplo, porque la base ya tiene cuentas duplicadas),
 * la aplicacion arranca igual y lo deja en el log: el bloqueo por cliente de
 * {@code CuentaServiceImpl} sigue evitando duplicados nuevos.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndiceCuentaActivaUnica implements ApplicationRunner {

    public static final String NOMBRE_INDICE = "uq_cuentas_cliente_tipo_activa";

    private static final String SQL_ESTADO_ACTIVA = """
            SELECT e.id FROM estados e
            JOIN tipo_estado te ON te.id = e.tipo_estado_id
            WHERE UPPER(e.nombre) = 'ACTIVA' AND UPPER(te.nombre) = 'CUENTA'
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        asegurarIndice();
    }

    /**
     * @return true si el indice existe al terminar (ya estaba o se acaba de crear)
     */
    public boolean asegurarIndice() {
        try {
            List<Long> estadosActiva = jdbcTemplate.queryForList(SQL_ESTADO_ACTIVA, Long.class);
            if (estadosActiva.size() != 1) {
                log.warn("No se crea el indice {}: se esperaba un estado ACTIVA de tipo CUENTA y hay {}",
                        NOMBRE_INDICE, estadosActiva.size());
                return false;
            }
            // El id sale de la propia base y es un Long: concatenarlo no abre
            // inyeccion. Un DDL no admite parametros enlazados en el WHERE.
            jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS " + NOMBRE_INDICE
                    + " ON cuentas (cliente_id, tipo_cuenta_id) WHERE estado_id = " + estadosActiva.get(0));
            return true;
        } catch (DataAccessException e) {
            log.warn("No se pudo crear el indice {}; revise si hay clientes con dos cuentas ACTIVA del mismo tipo",
                    NOMBRE_INDICE, e);
            return false;
        }
    }
}

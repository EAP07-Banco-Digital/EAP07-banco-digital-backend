package com.udea.bancodigital.cuentas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.udea.bancodigital.soporte.PostgresContenedor;
import com.udea.bancodigital.usuarios.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * HU16 de punta a punta contra PostgreSQL real: consultas JPQL (incluida la
 * suma con CASE), conversion de fechas y cuadre con el saldo de la cuenta.
 */
@AutoConfigureMockMvc
class ReporteMovimientosPostgresTest extends PostgresContenedor {

    private static final String CORREO = "reporte@test.local";
    private static final long CLIENTE_ID = 601L;
    private static final long OTRO_CLIENTE_ID = 602L;
    private static final String NUMERO = "6010000001";
    private static final String NUMERO_OTRO = "6020000001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private UsuarioService usuarioService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private long cuentaId;
    private long otraCuentaId;

    @BeforeEach
    void sembrarCuentasYMovimientos() {
        jdbcTemplate.update("DELETE FROM transacciones");
        jdbcTemplate.update("DELETE FROM cuentas");
        given(usuarioService.obtenerIdClientePorEmail(CORREO)).willReturn(CLIENTE_ID);

        // Saldo actual 115.000 = 50.000 - 20.000 + 15.000 (octubre) + 70.000 (noviembre)
        cuentaId = crearCuenta(CLIENTE_ID, NUMERO, "115000.00");
        otraCuentaId = crearCuenta(OTRO_CLIENTE_ID, NUMERO_OTRO, "5000.00");

        movimiento(cuentaId, null, "CREDITO", "50000.00", "2026-10-02T10:00:00Z", "Deposito");
        movimiento(cuentaId, otraCuentaId, "TRANSFERENCIA", "20000.00", "2026-10-10T10:00:00Z", "Pago");
        movimiento(otraCuentaId, cuentaId, "TRANSFERENCIA", "15000.00", "2026-10-31T23:59:59.999-05:00", "Devolucion");
        movimiento(cuentaId, null, "CREDITO", "80000.00", "2026-11-01T00:00:00-05:00", "Nomina");
        movimiento(cuentaId, null, "DEBITO", "10000.00", "2026-11-05T09:00:00Z", "Retiro");
    }

    private long crearCuenta(long clienteId, String numero, String saldo) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO cuentas (cliente_id, numero_cuenta, tipo_cuenta_id, saldo_disponible, estado_id, fecha_apertura)
                VALUES (?, ?, (SELECT id FROM tipo_cuenta WHERE nombre = 'AHORROS'), ?,
                        (SELECT id FROM estados WHERE nombre = 'ACTIVA'), '2026-09-01T00:00:00Z')
                RETURNING id
                """, Long.class, clienteId, numero, new BigDecimal(saldo));
    }

    private void movimiento(Long origen, Long destino, String tipo, String monto, String fecha, String descripcion) {
        jdbcTemplate.update("""
                INSERT INTO transacciones (cuenta_origen_id, cuenta_destino_id, tipo_transaccion_id, monto, estado, fecha_hora, descripcion)
                VALUES (?, ?, (SELECT id FROM tipo_transaccion WHERE nombre = ?), ?, 'EXITOSA', ?::timestamptz, ?)
                """, origen, destino, tipo, new BigDecimal(monto), fecha, descripcion);
    }

    private MvcResult pedirReporte(String numero, String inicio, String fin) throws Exception {
        return mockMvc.perform(get("/api/v1/cuentas/" + numero + "/reporte")
                        .with(user(CORREO).roles("CLIENTE"))
                        .param("fechaInicio", inicio)
                        .param("fechaFin", fin))
                .andReturn();
    }

    private JsonNode cuerpo(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString());
    }

    @Test
    void elReporteDeOctubreCuadraConElSaldoActualDeLaCuenta() throws Exception {
        MvcResult resultado = pedirReporte(NUMERO, "2026-10-01", "2026-10-31");
        JsonNode reporte = cuerpo(resultado);

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        // Noviembre movio +70.000: el cierre de octubre era 45.000 y el inicio 0.
        assertThat(reporte.get("saldoFinal").decimalValue()).isEqualByComparingTo("45000.00");
        assertThat(reporte.get("saldoInicial").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(reporte.get("totalCreditos").decimalValue()).isEqualByComparingTo("65000.00");
        assertThat(reporte.get("totalDebitos").decimalValue()).isEqualByComparingTo("20000.00");

        JsonNode transacciones = reporte.get("transacciones");
        // El ultimo milisegundo del 31 (hora de Colombia) entra; la medianoche del 1 de noviembre no.
        assertThat(transacciones).hasSize(3);
        assertThat(transacciones.get(0).get("descripcion").asText()).isEqualTo("Deposito");
        assertThat(transacciones.get(1).get("numeroCuentaDestino").asText()).isEqualTo(NUMERO_OTRO);
        assertThat(transacciones.get(2).get("naturaleza").asText()).isEqualTo("CREDITO");
        assertThat(transacciones.get(2).get("numeroCuentaOrigen").asText()).isEqualTo(NUMERO_OTRO);
        assertThat(transacciones.get(2).get("saldoResultante").decimalValue()).isEqualByComparingTo("45000.00");
    }

    @Test
    void elReporteHastaHoyTerminaEnElSaldoActual() throws Exception {
        JsonNode reporte = cuerpo(pedirReporte(NUMERO, "2026-10-01", "2026-11-30"));

        assertThat(reporte.get("saldoFinal").decimalValue()).isEqualByComparingTo("115000.00");
        assertThat(reporte.get("transacciones")).hasSize(5);
    }

    @Test
    void unPeriodoSinMovimientosDevuelveCerosYElSaldoDeEseMomento() throws Exception {
        MvcResult resultado = pedirReporte(NUMERO, "2026-10-11", "2026-10-30");
        JsonNode reporte = cuerpo(resultado);

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        assertThat(reporte.get("transacciones")).isEmpty();
        assertThat(reporte.get("totalCreditos").decimalValue()).isZero();
        assertThat(reporte.get("totalDebitos").decimalValue()).isZero();
        // Entre el pago del 10 y la devolucion del 31 el saldo fue 30.000.
        assertThat(reporte.get("saldoInicial").decimalValue()).isEqualByComparingTo("30000.00");
        assertThat(reporte.get("saldoFinal").decimalValue()).isEqualByComparingTo("30000.00");
    }

    @Test
    void unMovimientoDeLaNocheEnColombiaCuentaEnEseDiaAunqueEnUtcSeaElSiguiente() throws Exception {
        // 20 de octubre a las 9 p. m. en Bogota = 21 de octubre 02:00 UTC
        movimiento(cuentaId, null, "CREDITO", "1000.00", "2026-10-20T21:00:00-05:00", "Nocturno");
        jdbcTemplate.update("UPDATE cuentas SET saldo_disponible = saldo_disponible + 1000 WHERE id = ?", cuentaId);

        JsonNode delDia = cuerpo(pedirReporte(NUMERO, "2026-10-20", "2026-10-20"));
        JsonNode delSiguiente = cuerpo(pedirReporte(NUMERO, "2026-10-21", "2026-10-21"));

        assertThat(delDia.get("transacciones")).hasSize(1);
        assertThat(delDia.get("totalCreditos").decimalValue()).isEqualByComparingTo("1000.00");
        assertThat(delDia.get("saldoInicial").decimalValue()).isEqualByComparingTo("30000.00");
        assertThat(delDia.get("saldoFinal").decimalValue()).isEqualByComparingTo("31000.00");
        assertThat(delSiguiente.get("transacciones")).isEmpty();
    }

    @Test
    void elReporteDeUnaCuentaAjenaResponde403() throws Exception {
        MvcResult resultado = pedirReporte(NUMERO_OTRO, "2026-10-01", "2026-10-31");

        assertThat(resultado.getResponse().getStatus()).isEqualTo(403);
        assertThat(cuerpo(resultado).get("errorCode").asText()).isEqualTo("FORBIDDEN");
    }

    @Test
    void unRangoInvertidoResponde400() throws Exception {
        MvcResult resultado = pedirReporte(NUMERO, "2026-10-31", "2026-10-01");

        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(cuerpo(resultado).get("errorCode").asText()).isEqualTo("RANGO_FECHAS_INVALIDO");
    }
}

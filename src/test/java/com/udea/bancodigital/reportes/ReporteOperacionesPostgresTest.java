package com.udea.bancodigital.reportes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.udea.bancodigital.soporte.PostgresContenedor;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * HU20 de punta a punta contra PostgreSQL real: filtros, cruce de los tres
 * modulos y numero de consultas constante (sin N+1).
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ReporteOperacionesPostgresTest extends PostgresContenedor {

    private static final String URL = "/api/v1/admin/reportes/operaciones";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private long anaId;
    private long luisId;
    private long cuentaAna;
    private long cuentaLuis;
    private long cuentaLuis2;

    @BeforeEach
    void sembrar() {
        jdbcTemplate.update("DELETE FROM transacciones");
        jdbcTemplate.update("DELETE FROM cuentas");
        jdbcTemplate.update("DELETE FROM clientes");

        anaId = cliente("Ana", "Perez", "111");
        luisId = cliente("Luis", "Gomez", "222");
        cuentaAna = cuenta(anaId, "7010000001", "AHORROS");
        cuentaLuis = cuenta(luisId, "7020000001", "AHORROS");
        // CORRIENTE: un cliente no puede tener dos AHORROS activas (DEF-HU5-02).
        cuentaLuis2 = cuenta(luisId, "7020000002", "CORRIENTE");

        // Octubre: 3 operaciones. Noviembre: 1 (fuera del periodo).
        operacion(cuentaAna, cuentaLuis, "TRANSFERENCIA", "100000.00", "2026-10-05T10:00:00Z");
        operacion(cuentaLuis, null, "CREDITO", "50000.00", "2026-10-06T10:00:00Z");
        operacion(cuentaLuis2, cuentaAna, "TRANSFERENCIA", "35000.50", "2026-10-31T23:00:00Z");
        operacion(cuentaAna, null, "DEBITO", "1000.00", "2026-11-01T00:00:00Z");
    }

    private long cliente(String nombres, String apellidos, String documento) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO clientes (nombres, apellidos, tipo_documento, numero_documento, email, telefono, direccion, fecha_registro)
                VALUES (?, ?, 'CC', ?, ?, '3000000000', 'Calle 1', now()) RETURNING id
                """, Long.class, nombres, apellidos, documento, nombres.toLowerCase() + "@test.local");
    }

    private long cuenta(long clienteId, String numero, String tipo) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO cuentas (cliente_id, numero_cuenta, tipo_cuenta_id, saldo_disponible, estado_id, fecha_apertura)
                VALUES (?, ?, (SELECT id FROM tipo_cuenta WHERE nombre = ?), 0,
                        (SELECT id FROM estados WHERE nombre = 'ACTIVA'), now())
                RETURNING id
                """, Long.class, clienteId, numero, tipo);
    }

    private void operacion(Long origen, Long destino, String tipo, String monto, String fecha) {
        jdbcTemplate.update("""
                INSERT INTO transacciones (cuenta_origen_id, cuenta_destino_id, tipo_transaccion_id, monto, estado, fecha_hora, descripcion)
                VALUES (?, ?, (SELECT id FROM tipo_transaccion WHERE nombre = ?), ?, 'EXITOSA', ?::timestamptz, 'semilla')
                """, origen, destino, tipo, new BigDecimal(monto), fecha);
    }

    private JsonNode comoAdmin(MockHttpServletRequestBuilder peticion, int statusEsperado) throws Exception {
        MvcResult resultado = mockMvc.perform(peticion.with(user("admin@test.local").roles("ADMIN"))).andReturn();
        assertThat(resultado.getResponse().getStatus()).isEqualTo(statusEsperado);
        return objectMapper.readTree(resultado.getResponse().getContentAsString());
    }

    @Test
    void elReporteDeOctubreCruzaOperacionesCuentasYClientes() throws Exception {
        JsonNode reporte = comoAdmin(get(URL).param("fechaInicio", "2026-10-01").param("fechaFin", "2026-10-31"), 200);

        assertThat(reporte.at("/resumen/totalOperaciones").asInt()).isEqualTo(3);
        assertThat(reporte.at("/resumen/montoTotal").decimalValue()).isEqualByComparingTo("185000.50");
        assertThat(reporte.at("/resumen/porTipo/0/tipoTransaccion").asText()).isEqualTo("CREDITO");
        assertThat(reporte.at("/resumen/porTipo/1/cantidad").asInt()).isEqualTo(2);

        JsonNode primera = reporte.at("/operaciones/0");
        assertThat(primera.at("/origen/numeroCuenta").asText()).isEqualTo("7010000001");
        assertThat(primera.at("/origen/nombreCliente").asText()).isEqualTo("Ana Perez");
        assertThat(primera.at("/destino/nombreCliente").asText()).isEqualTo("Luis Gomez");
        assertThat(primera.at("/destino/numeroDocumento").asText()).isEqualTo("222");
        assertThat(reporte.at("/operaciones/1/destino").isNull()).isTrue();
    }

    @Test
    void elFiltroPorClienteIncluyeSusOperacionesComoOrigenYComoDestino() throws Exception {
        JsonNode reporte = comoAdmin(get(URL).param("clienteId", String.valueOf(anaId)), 200);

        // Ana: origen del 5/oct, destino del 31/oct y origen del debito de noviembre.
        assertThat(reporte.at("/resumen/totalOperaciones").asInt()).isEqualTo(3);
    }

    @Test
    void elFiltroPorCuentaYEstadoSeCombinan() throws Exception {
        JsonNode reporte = comoAdmin(get(URL).param("numeroCuenta", "7020000002").param("estado", "exitosa"), 200);

        assertThat(reporte.at("/resumen/totalOperaciones").asInt()).isEqualTo(1);
        assertThat(reporte.at("/operaciones/0/monto").decimalValue()).isEqualByComparingTo("35000.50");

        JsonNode otroEstado = comoAdmin(get(URL).param("estado", "RECHAZADA"), 200);
        assertThat(otroEstado.at("/resumen/totalOperaciones").asInt()).isZero();
    }

    @Test
    void unPeriodoSinOperacionesDevuelveCerosYDetalleVacio() throws Exception {
        JsonNode reporte = comoAdmin(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fechaInicio\":\"2025-01-01\",\"fechaFin\":\"2025-01-31\"}"), 200);

        assertThat(reporte.at("/resumen/totalOperaciones").asInt()).isZero();
        assertThat(reporte.at("/resumen/montoTotal").decimalValue()).isZero();
        assertThat(reporte.get("operaciones")).isEmpty();
    }

    @Test
    void unRangoInvertidoResponde400() throws Exception {
        JsonNode error = comoAdmin(get(URL).param("fechaInicio", "2026-10-31").param("fechaFin", "2026-10-01"), 400);

        assertThat(error.get("errorCode").asText()).isEqualTo("RANGO_FECHAS_INVALIDO");
    }

    @Test
    void elNumeroDeConsultasNoCreceConElNumeroDeOperaciones() throws Exception {
        Statistics estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        estadisticas.clear();
        comoAdmin(get(URL).param("fechaInicio", "2026-10-05").param("fechaFin", "2026-10-05"), 200);
        long consultasConUnaOperacion = estadisticas.getPrepareStatementCount();

        for (int i = 0; i < 20; i++) {
            operacion(i % 2 == 0 ? cuentaAna : cuentaLuis2, cuentaLuis, "TRANSFERENCIA", "1.00", "2026-10-05T11:00:00Z");
        }
        estadisticas.clear();
        JsonNode reporte = comoAdmin(get(URL).param("fechaInicio", "2026-10-05").param("fechaFin", "2026-10-05"), 200);
        long consultasConVeintiunaOperaciones = estadisticas.getPrepareStatementCount();

        assertThat(reporte.at("/resumen/totalOperaciones").asInt()).isEqualTo(21);
        assertThat(consultasConVeintiunaOperaciones).isEqualTo(consultasConUnaOperacion);
        assertThat(consultasConVeintiunaOperaciones).isLessThanOrEqualTo(5);
    }
}

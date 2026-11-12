package com.udea.bancodigital.cuentas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.udea.bancodigital.cuentas.config.IndiceCuentaActivaUnica;
import com.udea.bancodigital.soporte.PostgresContenedor;
import com.udea.bancodigital.usuarios.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * DEF-HU5-02 contra un PostgreSQL real: diez aperturas simultaneas del mismo
 * tipo para el mismo cliente dejan una sola cuenta ACTIVA.
 */
@AutoConfigureMockMvc
class AperturaCuentaConcurrenteTest extends PostgresContenedor {

    private static final int PETICIONES = 10;
    private static final String CORREO = "concurrencia@test.local";
    private static final long CLIENTE_ID = 501L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private IndiceCuentaActivaUnica indice;

    // Solo se reemplaza la resolucion correo -> cliente; el resto (servicio,
    // repositorios, transacciones, base) es el real.
    @MockitoBean
    private UsuarioService usuarioService;

    @BeforeEach
    void partirSinCuentas() {
        jdbcTemplate.update("DELETE FROM cuentas");
        given(usuarioService.obtenerIdClientePorEmail(CORREO)).willReturn(CLIENTE_ID);
    }

    private record Resultado(int status, String errorCode) {
    }

    private Resultado abrirCuenta(String tipo) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/cuentas")
                        .with(user(CORREO).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoCuenta\":\"" + tipo + "\"}"))
                .andReturn();
        JsonNode cuerpo = objectMapper.readTree(resultado.getResponse().getContentAsString());
        return new Resultado(resultado.getResponse().getStatus(), cuerpo.path("errorCode").asText(null));
    }

    private int cuentasActivas(String tipo) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM cuentas c
                JOIN estados e ON e.id = c.estado_id
                JOIN tipo_cuenta t ON t.id = c.tipo_cuenta_id
                WHERE c.cliente_id = ? AND e.nombre = 'ACTIVA' AND t.nombre = ?
                """, Integer.class, CLIENTE_ID, tipo);
    }

    @Test
    void diezAperturasSimultaneasDelMismoTipoCreanUnaSolaCuenta() throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(PETICIONES);
        CountDownLatch listos = new CountDownLatch(PETICIONES);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<Resultado>> futuros = new ArrayList<>();
            for (int i = 0; i < PETICIONES; i++) {
                futuros.add(hilos.submit(() -> {
                    listos.countDown();
                    largada.await();
                    return abrirCuenta("AHORROS");
                }));
            }
            // Todos los hilos esperan en la misma linea y salen a la vez.
            assertThat(listos.await(10, TimeUnit.SECONDS)).isTrue();
            largada.countDown();

            List<Resultado> resultados = new ArrayList<>();
            for (Future<Resultado> futuro : futuros) {
                resultados.add(futuro.get(30, TimeUnit.SECONDS));
            }

            assertThat(resultados).filteredOn(r -> r.status() == 201).hasSize(1);
            assertThat(resultados).filteredOn(r -> r.status() == 409)
                    .hasSize(PETICIONES - 1)
                    .allSatisfy(r -> assertThat(r.errorCode()).isEqualTo("ACCOUNT_TYPE_ALREADY_EXISTS"));
            assertThat(cuentasActivas("AHORROS")).isEqualTo(1);
        } finally {
            hilos.shutdownNow();
        }
    }

    @Test
    void laRespuestaDeLaAperturaConservaSuContrato() throws Exception {
        String cuerpo = mockMvc.perform(post("/api/v1/cuentas")
                        .with(user(CORREO).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoCuenta\":\"CORRIENTE\"}"))
                .andReturn().getResponse().getContentAsString();

        JsonNode cuenta = objectMapper.readTree(cuerpo);
        assertThat(cuenta.get("clienteId").asLong()).isEqualTo(CLIENTE_ID);
        assertThat(cuenta.get("numeroCuenta").asText()).hasSize(10).containsOnlyDigits();
        assertThat(cuenta.get("tipoCuenta").asText()).isEqualTo("CORRIENTE");
        assertThat(cuenta.get("saldoDisponible").decimalValue()).isZero();
        assertThat(cuenta.get("estado").asText()).isEqualTo("ACTIVA");
        assertThat(cuenta.has("id")).isTrue();
        assertThat(cuenta.has("fechaApertura")).isTrue();
    }

    @Test
    void elIndiceRechazaUnaSegundaCuentaActivaAunqueSeInserteSinPasarPorElServicio() throws Exception {
        assertThat(indice.asegurarIndice()).isTrue();
        assertThat(abrirCuenta("AHORROS").status()).isEqualTo(201);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO cuentas (cliente_id, numero_cuenta, tipo_cuenta_id, saldo_disponible, estado_id, fecha_apertura)
                SELECT c.cliente_id, '9999999999', c.tipo_cuenta_id, 0, c.estado_id, now() FROM cuentas c
                """))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(IndiceCuentaActivaUnica.NOMBRE_INDICE);
    }

    @Test
    void conLaCuentaAnteriorInactivaSePuedeAbrirOtraDelMismoTipo() throws Exception {
        assertThat(abrirCuenta("AHORROS").status()).isEqualTo(201);
        jdbcTemplate.update("""
                UPDATE cuentas SET estado_id = (SELECT id FROM estados WHERE nombre = 'INACTIVA')
                WHERE cliente_id = ?
                """, CLIENTE_ID);

        assertThat(abrirCuenta("AHORROS").status()).isEqualTo(201);
        assertThat(cuentasActivas("AHORROS")).isEqualTo(1);
    }
}

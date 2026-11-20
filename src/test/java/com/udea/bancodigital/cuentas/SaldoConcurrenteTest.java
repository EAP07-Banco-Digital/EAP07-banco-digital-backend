package com.udea.bancodigital.cuentas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.udea.bancodigital.soporte.PostgresContenedor;
import com.udea.bancodigital.usuarios.service.UsuarioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * DEF-HU14-01, DEF-HU14-02 y DEF-HU12-06 contra un PostgreSQL real: depósitos,
 * retiros y transferencias simultáneos sobre la misma cuenta no se pisan.
 */
@AutoConfigureMockMvc
class SaldoConcurrenteTest extends PostgresContenedor {

    private static final String CORREO = "saldo-concurrente@test.local";
    private static final long CLIENTE_ID = 502L;

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Solo se reemplaza la resolución correo -> cliente; el resto es real.
    @MockitoBean private UsuarioService usuarioService;

    private String numeroCuenta;

    @BeforeEach
    void abrirCuentaConSaldoCero() throws Exception {
        limpiar();
        given(usuarioService.obtenerIdClientePorEmail(CORREO)).willReturn(CLIENTE_ID);
        numeroCuenta = abrirCuenta("AHORROS");
    }

    // Primero transacciones y luego cuentas, por la clave foránea. Se limpia también
    // al terminar para no dejar filas que rompan a las otras pruebas de integración.
    @AfterEach
    void limpiar() {
        jdbcTemplate.update("DELETE FROM transacciones");
        jdbcTemplate.update("DELETE FROM cuentas");
    }

    // ---------------------------------------------------------------- pruebas

    @Test
    void veinteRetirosSimultaneosConSaldoParaUnoDejanPasarSoloUno() throws Exception {
        assertThat(operar("depositos", "100")).isEqualTo(201);

        List<Callable<Integer>> tareas = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tareas.add(() -> operar("retiros", "100"));
        }
        List<Integer> estados = lanzarEnParalelo(tareas);

        assertThat(estados).filteredOn(s -> s == 201).hasSize(1);
        assertThat(estados).filteredOn(s -> s == 400).hasSize(19);
        assertThat(saldoDe(numeroCuenta)).isEqualByComparingTo("0");
        assertThat(transaccionesDe(numeroCuenta, "DEBITO")).isEqualTo(1);
    }

    @Test
    void treintaDepositosSimultaneosSeReflejanTodosEnElSaldo() throws Exception {
        List<Callable<Integer>> tareas = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            tareas.add(() -> operar("depositos", "10"));
        }
        List<Integer> estados = lanzarEnParalelo(tareas);

        assertThat(estados).allMatch(s -> s == 201);
        assertThat(saldoDe(numeroCuenta)).isEqualByComparingTo("300");
        assertThat(transaccionesDe(numeroCuenta, "CREDITO")).isEqualTo(30);
    }

    @Test
    void depositosYTransferenciasSimultaneosNoSePisanEntreSi() throws Exception {
        String cuentaB = abrirCuenta("CORRIENTE");
        assertThat(operar("depositos", "1000")).isEqualTo(201);

        List<Callable<Integer>> tareas = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tareas.add(() -> transferir(numeroCuenta, cuentaB, "50"));
            tareas.add(() -> operar("depositos", "10"));
        }
        List<Integer> estados = lanzarEnParalelo(tareas);

        // 1000 - 10*50 + 10*10 = 600 en A; B recibe 500.
        assertThat(estados).allMatch(s -> s == 201);
        assertThat(saldoDe(numeroCuenta)).isEqualByComparingTo("600");
        assertThat(saldoDe(cuentaB)).isEqualByComparingTo("500");
    }

    // ---------------------------------------------------------------- ayudas

    private String abrirCuenta(String tipo) throws Exception {
        String cuerpo = mockMvc.perform(post("/api/v1/cuentas")
                        .with(user(CORREO).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoCuenta\":\"" + tipo + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cuerpo).get("numeroCuenta").asText();
    }

    private int operar(String recurso, String monto) throws Exception {
        return mockMvc.perform(post("/api/v1/cuentas/{numero}/{recurso}", numeroCuenta, recurso)
                        .with(user(CORREO).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monto\":" + monto + "}"))
                .andReturn().getResponse().getStatus();
    }

    private int transferir(String origen, String destino, String monto) throws Exception {
        return mockMvc.perform(post("/api/v1/transferencias")
                        .with(user(CORREO).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroCuentaOrigen\":\"" + origen + "\","
                                + "\"numeroCuentaDestino\":\"" + destino + "\","
                                + "\"monto\":" + monto + "}"))
                .andReturn().getResponse().getStatus();
    }

    // Todos los hilos esperan en la misma línea y salen a la vez.
    private List<Integer> lanzarEnParalelo(List<Callable<Integer>> tareas) throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(tareas.size());
        CountDownLatch listos = new CountDownLatch(tareas.size());
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<Integer>> futuros = new ArrayList<>();
            for (Callable<Integer> tarea : tareas) {
                futuros.add(hilos.submit(() -> {
                    listos.countDown();
                    largada.await();
                    return tarea.call();
                }));
            }
            assertThat(listos.await(10, TimeUnit.SECONDS)).isTrue();
            largada.countDown();

            List<Integer> estados = new ArrayList<>();
            for (Future<Integer> futuro : futuros) {
                estados.add(futuro.get(60, TimeUnit.SECONDS));
            }
            return estados;
        } finally {
            hilos.shutdownNow();
        }
    }

    private BigDecimal saldoDe(String numero) {
        return jdbcTemplate.queryForObject(
                "SELECT saldo_disponible FROM cuentas WHERE numero_cuenta = ?", BigDecimal.class, numero);
    }

    private int transaccionesDe(String numero, String tipo) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM transacciones AS t
                INNER JOIN tipo_transaccion AS tt
                    ON tt.id = t.tipo_transaccion_id
                INNER JOIN cuentas AS c
                    ON c.id = COALESCE(t.cuenta_origen_id, t.cuenta_destino_id)
                WHERE c.numero_cuenta = ?
                  AND tt.nombre = ?
                """, Integer.class, numero, tipo);
    }
}

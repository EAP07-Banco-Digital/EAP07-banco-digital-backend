package com.udea.bancodigital.shared.config;

import com.udea.bancodigital.soporte.PostgresContenedor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de integracion de la observabilidad: levanta la aplicacion completa
 * (servidor real en puerto aleatorio y PostgreSQL en Docker) y verifica lo que
 * Prometheus necesita para raspar las metricas, sin abrir mas de la cuenta.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.jpa.show-sql=false",
        "spring.jpa.defer-datasource-initialization=true",
        "spring.sql.init.mode=always",
        "spring.sql.init.data-locations=classpath:sql/catalogos-cuentas.sql",
        "jwt.secret=clave-de-pruebas-de-al-menos-32-bytes-para-hs256",
        "management.server.port=0"
})
class ObservabilidadActuatorTest extends PostgresContenedor {

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private Environment entorno;

    private HttpResponse<String> get(String puertoPropiedad, String ruta) throws IOException, InterruptedException {
        String puerto = entorno.getRequiredProperty(puertoPropiedad);
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).GET().build();
        return http.send(peticion, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> gestion(String ruta) throws IOException, InterruptedException {
        return get("local.management.port", ruta);
    }

    @Test
    void laSaludEsPublicaYReportaUp() throws Exception {
        HttpResponse<String> respuesta = gestion("/actuator/health");

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void lasSondasDeVidaYPreparacionResponden() throws Exception {
        assertThat(gestion("/actuator/health/liveness").statusCode()).isEqualTo(200);
        assertThat(gestion("/actuator/health/readiness").statusCode()).isEqualTo(200);
    }

    @Test
    void prometheusExponeMetricasHttpConLaEtiquetaDeLaAplicacion() throws Exception {
        // Una peticion a la API sin token deja una muestra 401 en http_server_requests.
        HttpResponse<String> sinToken = get("local.server.port", "/api/usuarios/me");
        assertThat(sinToken.statusCode()).isEqualTo(401);

        HttpResponse<String> respuesta = gestion("/actuator/prometheus");

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body())
                .contains("http_server_requests_seconds_count")
                .contains("application=\"banco-digital-backend\"")
                .contains("status=\"401\"")
                .contains("hikaricp_connections_active")
                .contains("jvm_memory_used_bytes");
    }

    @Test
    void elRestoDeActuatorExigeToken() throws Exception {
        assertThat(gestion("/actuator/metrics").statusCode()).isEqualTo(401);
    }

    @Test
    void elPuertoPublicoNoSirveLasMetricas() throws Exception {
        HttpResponse<String> respuesta = get("local.server.port", "/actuator/prometheus");

        assertThat(respuesta.statusCode()).isNotEqualTo(200);
        assertThat(respuesta.body()).doesNotContain("http_server_requests_seconds_count");
    }
}

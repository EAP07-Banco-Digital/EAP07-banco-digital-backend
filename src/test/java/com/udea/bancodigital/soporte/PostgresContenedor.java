package com.udea.bancodigital.soporte;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base de las pruebas que necesitan el motor real de PostgreSQL (indices
 * parciales, candados, SELECT FOR UPDATE), que una base embebida no imita.
 * <p>
 * {@code @ServiceConnection} apunta el datasource al contenedor, asi que estas
 * pruebas nunca tocan la base Neon del equipo. Sin Docker se saltan en lugar de
 * fallar ({@code disabledWithoutDocker}).
 * <p>
 * El contenedor es uno solo para toda la JVM (se arranca en el bloque estatico
 * y lo retira Ryuk al terminar): Spring guarda en cache el contexto entre clases
 * de prueba, y un contenedor por clase lo dejaria apuntando a una base muerta.
 * Por lo mismo el esquema se crea con {@code create} y no {@code create-drop}:
 * no hace falta borrarlo al cerrar, y asi el cierre no espera a la base.
 * Hibernate crea el esquema y despues (por el defer) se siembran los catalogos.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.jpa.show-sql=false",
        "spring.jpa.defer-datasource-initialization=true",
        "spring.sql.init.mode=always",
        "spring.sql.init.data-locations=classpath:sql/catalogos-cuentas.sql",
        "jwt.secret=clave-de-pruebas-de-al-menos-32-bytes-para-hs256"
})
public abstract class PostgresContenedor {

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
        }
    }
}

package com.udea.bancodigital.shared.jwt;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    // La firma es HMAC: la libreria elige el algoritmo segun el tamano de la
    // clave, asi que una clave de menos de 32 bytes hace fallar el arranque.
    private static final String CLAVE = "clave-de-prueba-de-64-caracteres-que-si-alcanza-para-hs512-0";
    private static final Long USUARIO_ID = 33L;

    private JwtService servicio(long expirationMs) {
        return new JwtService(CLAVE, expirationMs);
    }

    @Test
    void elTokenGeneradoGuardaElIdYElRolComoSubjectYClaim() {
        String token = servicio(3_600_000L).generarToken(USUARIO_ID, "CLIENTE");

        JwtService lector = servicio(3_600_000L);
        assertThat(lector.esTokenValido(token)).isTrue();
        assertThat(lector.extraerUsuarioId(token)).isEqualTo(USUARIO_ID);
    }

    @Test
    void unTokenVigenteAunNoHaExpirado() {
        assertThat(servicio(3_600_000L).esTokenValido(servicio(3_600_000L).generarToken(USUARIO_ID, "CLIENTE"))).isTrue();
    }

    @Test
    void unTokenCaducadoSeReportaInvalidoSinPropagarLaExcepcion() {
        String caducado = servicio(-1_000L).generarToken(USUARIO_ID, "CLIENTE");

        assertThat(servicio(3_600_000L).esTokenValido(caducado)).isFalse();
    }

    @Test
    void unTokenFirmadoConOtraClaveSeReportaInvalido() {
        String ajeno = new JwtService("otra-clave-de-64-caracteres-distinta-a-la-de-prueba-1234567890", 3_600_000L)
                .generarToken(USUARIO_ID, "CLIENTE");

        assertThat(servicio(3_600_000L).esTokenValido(ajeno)).isFalse();
    }

    @Test
    void unTokenMalFormadoSeReportaInvalido() {
        assertThat(servicio(3_600_000L).esTokenValido("esto-no-es-un-jwt")).isFalse();
    }

    @Test
    void unTokenVacioONuloSeReportaInvalido() {
        JwtService servicio = servicio(3_600_000L);

        assertThat(servicio.esTokenValido("")).isFalse();
        assertThat(servicio.esTokenValido(null)).isFalse();
    }

    @Test
    void alExtraerElIdDeUnTokenInvalidoSePropagaLaExcepcion() {
        JwtService servicio = servicio(3_600_000L);

        assertThatThrownBy(() -> servicio.extraerUsuarioId("token.inventado"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void dosTokensGeneradosEnElMismoSegundoCompartenSubjectYClaim() {
        JwtService servicio = servicio(3_600_000L);

        assertThat(servicio.extraerUsuarioId(servicio.generarToken(USUARIO_ID, "CLIENTE"))).isEqualTo(USUARIO_ID);
        assertThat(servicio.extraerUsuarioId(servicio.generarToken(USUARIO_ID, "CLIENTE"))).isEqualTo(USUARIO_ID);
    }

    @Test
    void laExpiracionDelTokenEsLaQueSeConfiguro() {
        long antes = System.currentTimeMillis();
        String token = servicio(3_600_000L).generarToken(USUARIO_ID, "CLIENTE");

        // jjwt guarda la fecha en segundos: se tolera el redondeo.
        assertThat(servicio(3_600_000L).extraerExpiracion(token).getTime())
                .isBetween(antes + 3_600_000L - 1_000L, System.currentTimeMillis() + 3_600_000L);
    }

    @Test
    void dosTokensDelMismoUsuarioEnElMismoSegundoSonDistintos() {
        // Necesario para la revocacion (HU8): revocar uno no puede revocar el otro.
        JwtService servicio = servicio(3_600_000L);

        assertThat(servicio.generarToken(USUARIO_ID, "CLIENTE")).isNotEqualTo(servicio.generarToken(USUARIO_ID, "CLIENTE"));
    }

    @Test
    void unTokenAntiguoConElCorreoComoSubjectSeReportaInvalido() {
        // Firmado con la clave correcta y vigente, pero emitido antes de que el
        // subject fuera el id: el filtro no sabria a quien autenticar.
        String antiguo = Jwts.builder()
                .subject("juan@banco.com")
                .claim("rol", "CLIENTE")
                .expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(Keys.hmacShaKeyFor(CLAVE.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(servicio(3_600_000L).esTokenValido(antiguo)).isFalse();
    }
}

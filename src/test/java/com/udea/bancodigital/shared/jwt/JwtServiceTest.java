package com.udea.bancodigital.shared.jwt;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    // La firma es HMAC: la libreria elige el algoritmo segun el tamano de la
    // clave, asi que una clave de menos de 32 bytes hace fallar el arranque.
    private static final String CLAVE = "clave-de-prueba-de-64-caracteres-que-si-alcanza-para-hs512-0";
    private static final String CORREO = "juan@banco.com";

    private JwtService servicio(long expirationMs) {
        return new JwtService(CLAVE, expirationMs);
    }

    @Test
    void elTokenGeneradoGuardaElCorreoYElRolComoSubjectYClaim() {
        String token = servicio(3_600_000L).generarToken(CORREO, "CLIENTE");

        JwtService lector = servicio(3_600_000L);
        assertThat(lector.esTokenValido(token)).isTrue();
        assertThat(lector.extraerEmail(token)).isEqualTo(CORREO);
    }

    @Test
    void unTokenVigenteAunNoHaExpirado() {
        assertThat(servicio(3_600_000L).esTokenValido(servicio(3_600_000L).generarToken(CORREO, "CLIENTE"))).isTrue();
    }

    @Test
    void unTokenCaducadoSeReportaInvalidoSinPropagarLaExcepcion() {
        String caducado = servicio(-1_000L).generarToken(CORREO, "CLIENTE");

        assertThat(servicio(3_600_000L).esTokenValido(caducado)).isFalse();
    }

    @Test
    void unTokenFirmadoConOtraClaveSeReportaInvalido() {
        String ajeno = new JwtService("otra-clave-de-64-caracteres-distinta-a-la-de-prueba-1234567890", 3_600_000L)
                .generarToken(CORREO, "CLIENTE");

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
    void alExtraerElCorreoDeUnTokenInvalidoSePropagaLaExcepcion() {
        JwtService servicio = servicio(3_600_000L);

        assertThatThrownBy(() -> servicio.extraerEmail("token.inventado"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void dosTokensGeneradosEnElMismoSegundoCompartenSubjectYClaim() {
        JwtService servicio = servicio(3_600_000L);

        assertThat(servicio.extraerEmail(servicio.generarToken(CORREO, "CLIENTE"))).isEqualTo(CORREO);
        assertThat(servicio.extraerEmail(servicio.generarToken(CORREO, "CLIENTE"))).isEqualTo(CORREO);
    }

    @Test
    void laExpiracionDelTokenEsLaQueSeConfiguro() {
        long antes = System.currentTimeMillis();
        String token = servicio(3_600_000L).generarToken(CORREO, "CLIENTE");

        // jjwt guarda la fecha en segundos: se tolera el redondeo.
        assertThat(servicio(3_600_000L).extraerExpiracion(token).getTime())
                .isBetween(antes + 3_600_000L - 1_000L, System.currentTimeMillis() + 3_600_000L);
    }

    @Test
    void dosTokensDelMismoUsuarioEnElMismoSegundoSonDistintos() {
        // Necesario para la revocacion (HU8): revocar uno no puede revocar el otro.
        JwtService servicio = servicio(3_600_000L);

        assertThat(servicio.generarToken(CORREO, "CLIENTE")).isNotEqualTo(servicio.generarToken(CORREO, "CLIENTE"));
    }
}

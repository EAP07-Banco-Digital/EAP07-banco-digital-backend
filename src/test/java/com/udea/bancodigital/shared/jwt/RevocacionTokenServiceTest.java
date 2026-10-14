package com.udea.bancodigital.shared.jwt;

import com.udea.bancodigital.shared.entity.TokenRevocado;
import com.udea.bancodigital.shared.repository.TokenRevocadoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RevocacionTokenServiceTest {

    private static final String TOKEN = "jwt.ficticio.firmado";
    // SHA-256 de TOKEN, calculado aparte: fija el formato (hex en minusculas).
    private static final String HASH_TOKEN = "8b5b69867366b107218aa1612d646da8db60466dde5ca828fe157954974d5086";

    @Mock
    private TokenRevocadoRepository tokenRevocadoRepository;

    @Mock
    private JwtService jwtService;

    private RevocacionTokenService servicio() {
        return new RevocacionTokenService(tokenRevocadoRepository, jwtService);
    }

    @Test
    void revocarGuardaElHashDelTokenYSuExpiracionNuncaElTokenEnClaro() {
        Date expiracion = new Date(1_900_000_000_000L);
        given(jwtService.extraerExpiracion(TOKEN)).willReturn(expiracion);

        Instant antes = Instant.now();
        servicio().revocar(TOKEN);

        ArgumentCaptor<TokenRevocado> captor = ArgumentCaptor.forClass(TokenRevocado.class);
        verify(tokenRevocadoRepository).save(captor.capture());
        TokenRevocado guardado = captor.getValue();
        assertThat(guardado.getTokenHash()).isEqualTo(HASH_TOKEN);
        assertThat(guardado.getFechaExpiracion()).isEqualTo(expiracion.toInstant());
        assertThat(guardado.getFechaRevocacion()).isBetween(antes, Instant.now());
    }

    @Test
    void revocarYConsultarUsanElMismoHash() {
        given(jwtService.extraerExpiracion(TOKEN)).willReturn(new Date());

        servicio().revocar(TOKEN);
        ArgumentCaptor<TokenRevocado> captor = ArgumentCaptor.forClass(TokenRevocado.class);
        verify(tokenRevocadoRepository).save(captor.capture());

        given(tokenRevocadoRepository.existsByTokenHash(captor.getValue().getTokenHash())).willReturn(true);
        assertThat(servicio().estaRevocado(TOKEN)).isTrue();
    }

    @Test
    void unTokenQueNoEstaEnLaListaNegraNoEstaRevocado() {
        given(tokenRevocadoRepository.existsByTokenHash(HASH_TOKEN)).willReturn(false);

        assertThat(servicio().estaRevocado(TOKEN)).isFalse();
    }

    @Test
    void revocarUnTokenYaRevocadoNoDuplicaLaFila() {
        given(tokenRevocadoRepository.existsByTokenHash(anyString())).willReturn(true);

        servicio().revocar(TOKEN);

        verify(tokenRevocadoRepository, never()).save(any());
    }

    @Test
    void tokensDistintosProducenHashesDistintos() {
        given(jwtService.extraerExpiracion(anyString())).willReturn(new Date());

        servicio().revocar(TOKEN);
        servicio().revocar(TOKEN + "x");

        ArgumentCaptor<TokenRevocado> captor = ArgumentCaptor.forClass(TokenRevocado.class);
        verify(tokenRevocadoRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getTokenHash())
                .isNotEqualTo(captor.getAllValues().get(1).getTokenHash());
    }

    @Test
    void purgarExpiradosBorraLoVencidoAntesDelInstanteDadoYDevuelveCuantos() {
        Instant ahora = Instant.parse("2026-10-06T18:00:00Z");
        given(tokenRevocadoRepository.eliminarExpiradosAntesDe(ahora)).willReturn(3);

        assertThat(servicio().purgarExpirados(ahora)).isEqualTo(3);
        verify(tokenRevocadoRepository).eliminarExpiradosAntesDe(ahora);
    }
}

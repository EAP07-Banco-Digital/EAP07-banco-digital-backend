package com.udea.bancodigital.shared.jwt;

import com.udea.bancodigital.usuarios.api.UsuarioApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String CORREO = "juan@banco.com";
    private static final String TOKEN = "jwt.ficticio.firmado";

    @Mock
    private JwtService jwtService;

    @Mock
    private UsuarioApi usuarioApi;

    @Mock
    private RevocacionTokenService revocacionTokenService;

    @InjectMocks
    private JwtAuthenticationFilter filtro;

    private MockHttpServletRequest pasar(String headerAuthorization) throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/usuarios/me");
        if (headerAuthorization != null) {
            peticion.addHeader("Authorization", headerAuthorization);
        }
        MockFilterChain cadena = new MockFilterChain();
        filtro.doFilter(peticion, new MockHttpServletResponse(), cadena);
        // El filtro nunca corta la peticion: siempre la deja seguir.
        assertThat(cadena.getRequest()).isSameAs(peticion);
        return peticion;
    }

    private Authentication autenticacionActual() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @AfterEach
    void limpiarElContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sinHeaderAuthorizationLaPeticionSigueSinAutenticar() throws Exception {
        pasar(null);

        assertThat(autenticacionActual()).isNull();
        verifyNoInteractions(jwtService, usuarioApi, revocacionTokenService);
    }

    @Test
    void unHeaderQueNoEsBearerNoSeIntentaValidar() throws Exception {
        pasar("Basic YXVuOmNvcm5lcGFzcw==");

        assertThat(autenticacionActual()).isNull();
        verifyNoInteractions(jwtService, usuarioApi);
    }

    @Test
    void unTokenInvalidoNoAutentica() throws Exception {
        given(jwtService.esTokenValido(TOKEN)).willReturn(false);

        pasar("Bearer " + TOKEN);

        assertThat(autenticacionActual()).isNull();
        // La peticion no se corta aqui: responder 401 es trabajo de la cadena
        // de seguridad, no del filtro.
        verifyNoInteractions(usuarioApi, revocacionTokenService);
    }

    @Test
    void unTokenValidoAutenticaConElCorreoYElRolDelUsuario() throws Exception {
        given(jwtService.esTokenValido(TOKEN)).willReturn(true);
        given(jwtService.extraerEmail(TOKEN)).willReturn(CORREO);
        given(usuarioApi.obtenerRolPorEmail(CORREO)).willReturn(Optional.of("CLIENTE"));

        pasar("Bearer " + TOKEN);

        assertThat(autenticacionActual()).isNotNull();
        assertThat(autenticacionActual().getName()).isEqualTo(CORREO);
        assertThat(autenticacionActual().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_CLIENTE");
    }

    @Test
    void unRolDistintoDeClienteSeTraduceEnUnaAutoridadDistinta() throws Exception {
        given(jwtService.esTokenValido(TOKEN)).willReturn(true);
        given(jwtService.extraerEmail(TOKEN)).willReturn(CORREO);
        given(usuarioApi.obtenerRolPorEmail(CORREO)).willReturn(Optional.of("ADMIN"));

        pasar("Bearer " + TOKEN);

        assertThat(autenticacionActual().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void unTokenDeUnUsuarioQueYaNoExisteNoAutentica() throws Exception {
        // El filtro relee el usuario en cada peticion, pero de el solo toma el
        // rol: no consulta el estado, asi que desactivar a alguien no le quita el
        // acceso hasta que su token expira (DEF-T-01).
        given(jwtService.esTokenValido(TOKEN)).willReturn(true);
        given(jwtService.extraerEmail(TOKEN)).willReturn(CORREO);
        given(usuarioApi.obtenerRolPorEmail(CORREO)).willReturn(Optional.empty());

        pasar("Bearer " + TOKEN);

        assertThat(autenticacionActual()).isNull();
    }

    @Test
    void laAutenticacionNoExponeElTokenComoCredencial() throws Exception {
        given(jwtService.esTokenValido(anyString())).willReturn(true);
        when(jwtService.extraerEmail(anyString())).thenReturn(CORREO);
        given(usuarioApi.obtenerRolPorEmail(CORREO)).willReturn(Optional.of("CLIENTE"));

        pasar("Bearer " + TOKEN);

        assertThat(autenticacionActual().getCredentials()).isNull();
    }

    @Test
    void unTokenRevocadoNoAutenticaYDejaLaMarcaParaElEntryPoint() throws Exception {
        given(jwtService.esTokenValido(TOKEN)).willReturn(true);
        given(revocacionTokenService.estaRevocado(TOKEN)).willReturn(true);

        MockHttpServletRequest peticion = pasar("Bearer " + TOKEN);

        assertThat(autenticacionActual()).isNull();
        assertThat(peticion.getAttribute(JwtAuthenticationFilter.TOKEN_REVOCADO_ATTR)).isEqualTo(Boolean.TRUE);
        // Revocado gana: ni siquiera se busca al usuario.
        verifyNoInteractions(usuarioApi);
    }

    @Test
    void unTokenVigenteNoRevocadoNoDejaLaMarcaDeRevocado() throws Exception {
        given(jwtService.esTokenValido(TOKEN)).willReturn(true);
        given(jwtService.extraerEmail(TOKEN)).willReturn(CORREO);
        given(usuarioApi.obtenerRolPorEmail(CORREO)).willReturn(Optional.of("CLIENTE"));

        MockHttpServletRequest peticion = pasar("Bearer " + TOKEN);

        assertThat(peticion.getAttribute(JwtAuthenticationFilter.TOKEN_REVOCADO_ATTR)).isNull();
        assertThat(autenticacionActual()).isNotNull();
    }
}

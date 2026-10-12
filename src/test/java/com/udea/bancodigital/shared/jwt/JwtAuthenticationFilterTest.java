package com.udea.bancodigital.shared.jwt;

import com.udea.bancodigital.shared.entity.Estado;
import com.udea.bancodigital.usuarios.entity.Rol;
import com.udea.bancodigital.usuarios.entity.Usuario;
import com.udea.bancodigital.usuarios.repository.UsuarioRepository;
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
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private JwtAuthenticationFilter filtro;

    private Usuario usuarioConRol(String rol) {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setEmail(CORREO);
        usuario.setRol(new Rol(1L, rol));
        Estado estado = new Estado();
        estado.setNombre("ACTIVO");
        usuario.setEstado(estado);
        return usuario;
    }

    private MockHttpServletResponse pasar(String headerAuthorization) throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/usuarios/me");
        if (headerAuthorization != null) {
            peticion.addHeader("Authorization", headerAuthorization);
        }
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        filtro.doFilter(peticion, respuesta, new MockFilterChain());
        return respuesta;
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
        verifyNoInteractions(jwtService, usuarioRepository);
    }

    @Test
    void unHeaderQueNoEsBearerNoSeIntentaValidar() throws Exception {
        pasar("Basic YXVuOmNvcm5lcGFzcw==");

        assertThat(autenticacionActual()).isNull();
        verifyNoInteractions(jwtService, usuarioRepository);
    }

    @Test
    void unTokenInvalidoNoAutentica() throws Exception {
        given(jwtService.esTokenValido(TOKEN)).willReturn(false);

        pasar("Bearer " + TOKEN);

        assertThat(autenticacionActual()).isNull();
        // La peticion no se corta aqui: responder 401 es trabajo de la cadena
        // de seguridad, no del filtro.
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    void unTokenValidoAutenticaConElCorreoYElRolDelUsuario() throws Exception {
        given(jwtService.esTokenValido(TOKEN)).willReturn(true);
        given(jwtService.extraerEmail(TOKEN)).willReturn(CORREO);
        given(usuarioRepository.findByEmail(CORREO)).willReturn(Optional.of(usuarioConRol("CLIENTE")));

        pasar("Bearer " + TOKEN);

        assertThat(autenticacionActual()).isNotNull();
        assertThat(autenticacionActual().getName()).isEqualTo(CORREO);
        assertThat(autenticacionActual().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_CLIENTE");
    }

    @Test
    void unTokenValidoDeUnUsuarioInactivoNoAutentica() throws Exception {
        given(jwtService.esTokenValido(TOKEN)).willReturn(true);
        given(jwtService.extraerEmail(TOKEN)).willReturn(CORREO);
        Usuario usuario = usuarioConRol("CLIENTE");
        Estado estado = new Estado();
        estado.setNombre("INACTIVO");
        usuario.setEstado(estado);
        given(usuarioRepository.findByEmail(CORREO)).willReturn(Optional.of(usuario));

        pasar("Bearer " + TOKEN);

        assertThat(autenticacionActual()).isNull();
    }

    @Test
    void unRolDistintoDeClienteSeTraduceEnUnaAutoridadDistinta() throws Exception {
        given(jwtService.esTokenValido(TOKEN)).willReturn(true);
        given(jwtService.extraerEmail(TOKEN)).willReturn(CORREO);
        given(usuarioRepository.findByEmail(CORREO)).willReturn(Optional.of(usuarioConRol("ADMIN")));

        pasar("Bearer " + TOKEN);

        assertThat(autenticacionActual().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void unTokenDeUnUsuarioQueYaNoExisteNoAutentica() throws Exception {
        given(jwtService.esTokenValido(TOKEN)).willReturn(true);
        given(jwtService.extraerEmail(TOKEN)).willReturn(CORREO);
        given(usuarioRepository.findByEmail(CORREO)).willReturn(Optional.empty());

        pasar("Bearer " + TOKEN);

        assertThat(autenticacionActual()).isNull();
    }

    @Test
    void laAutenticacionNoExponeElTokenComoCredencial() throws Exception {
        given(jwtService.esTokenValido(anyString())).willReturn(true);
        when(jwtService.extraerEmail(anyString())).thenReturn(CORREO);
        given(usuarioRepository.findByEmail(CORREO)).willReturn(Optional.of(usuarioConRol("CLIENTE")));

        pasar("Bearer " + TOKEN);

        assertThat(autenticacionActual().getCredentials()).isNull();
    }
}

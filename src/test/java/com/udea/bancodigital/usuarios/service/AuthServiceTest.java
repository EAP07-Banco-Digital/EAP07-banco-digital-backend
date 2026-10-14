package com.udea.bancodigital.usuarios.service;

import com.udea.bancodigital.shared.entity.Estado;
import com.udea.bancodigital.shared.jwt.JwtService;
import com.udea.bancodigital.shared.jwt.RevocacionTokenService;
import com.udea.bancodigital.usuarios.dto.LoginRequestDTO;
import com.udea.bancodigital.usuarios.dto.LoginResponseDTO;
import com.udea.bancodigital.usuarios.entity.Rol;
import com.udea.bancodigital.usuarios.entity.Usuario;
import com.udea.bancodigital.usuarios.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String CORREO = "juan@banco.com";
    private static final String CLAVE = "Segura123!";
    private static final String CLAVE_ENCRIPTADA = "$2a$10$hashFicticioDePrueba";

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RevocacionTokenService revocacionTokenService;

    @InjectMocks
    private AuthService authService;

    private Usuario usuarioConEstado(String nombreEstado) {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setEmail(CORREO);
        usuario.setPasswordHash(CLAVE_ENCRIPTADA);
        usuario.setFechaNacimiento(LocalDate.of(1998, 4, 15));
        usuario.setRol(new Rol(1L, "CLIENTE"));
        usuario.setEstado(new Estado(1L, null, nombreEstado));
        return usuario;
    }

    @Test
    void loginExitosoDevuelveElTokenYTipoBearer() {
        given(usuarioRepository.findByEmail(CORREO)).willReturn(Optional.of(usuarioConEstado("ACTIVO")));
        given(passwordEncoder.matches(CLAVE, CLAVE_ENCRIPTADA)).willReturn(true);
        given(jwtService.generarToken(CORREO, "CLIENTE")).willReturn("jwt.ficticio.firmado");

        LoginResponseDTO respuesta = authService.login(new LoginRequestDTO(CORREO, CLAVE));

        assertThat(respuesta.getToken()).isEqualTo("jwt.ficticio.firmado");
        assertThat(respuesta.getTipo()).isEqualTo("Bearer");
    }

    @Test
    void loginExitosoFirmaElTokenConElRolDelUsuario() {
        Usuario usuario = usuarioConEstado("ACTIVO");
        usuario.setRol(new Rol(2L, "ADMIN"));
        given(usuarioRepository.findByEmail(CORREO)).willReturn(Optional.of(usuario));
        given(passwordEncoder.matches(CLAVE, CLAVE_ENCRIPTADA)).willReturn(true);
        given(jwtService.generarToken(anyString(), anyString())).willReturn("jwt.ficticio.firmado");

        authService.login(new LoginRequestDTO(CORREO, CLAVE));

        verify(jwtService).generarToken(CORREO, "ADMIN");
    }

    @Test
    void loginConCorreoNoRegistradoFallaConCredencialesInvalidas() {
        // Un Optional vacio es el retorno por defecto del mock: el usuario no existe.
        assertThatThrownBy(() -> authService.login(new LoginRequestDTO("nadie@banco.com", CLAVE)))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Credenciales invalidas");

        verifyNoInteractions(passwordEncoder, jwtService);
    }

    @Test
    void loginConContrasenaIncorrectaFallaYNoEmiteToken() {
        given(usuarioRepository.findByEmail(CORREO)).willReturn(Optional.of(usuarioConEstado("ACTIVO")));
        given(passwordEncoder.matches("Incorrecta1!", CLAVE_ENCRIPTADA)).willReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequestDTO(CORREO, "Incorrecta1!")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Credenciales invalidas");

        verify(jwtService, never()).generarToken(anyString(), anyString());
    }

    @Test
    void loginConUsuarioInactivoFallaAunqueLaContrasenaSeaCorrecta() {
        given(usuarioRepository.findByEmail(CORREO)).willReturn(Optional.of(usuarioConEstado("INACTIVO")));
        given(passwordEncoder.matches(CLAVE, CLAVE_ENCRIPTADA)).willReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequestDTO(CORREO, CLAVE)))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("El usuario no esta activo");

        verify(jwtService, never()).generarToken(anyString(), anyString());
    }

    @Test
    void loginAceptaElEstadoActivoEscritoEnMinusculas() {
        given(usuarioRepository.findByEmail(CORREO)).willReturn(Optional.of(usuarioConEstado("activo")));
        given(passwordEncoder.matches(CLAVE, CLAVE_ENCRIPTADA)).willReturn(true);
        given(jwtService.generarToken(CORREO, "CLIENTE")).willReturn("jwt.ficticio.firmado");

        assertThat(authService.login(new LoginRequestDTO(CORREO, CLAVE)).getToken())
                .isEqualTo("jwt.ficticio.firmado");
    }

    @Test
    void loginNoRevelaSiElCorreoEstaRegistrado() {
        // Mismo mensaje en ambos fallos: un atacante no puede enumerar correos
        // probando el login y leyendo la respuesta.
        assertThatThrownBy(() -> authService.login(new LoginRequestDTO("nadie@banco.com", CLAVE)))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Credenciales invalidas");

        given(usuarioRepository.findByEmail(CORREO)).willReturn(Optional.of(usuarioConEstado("ACTIVO")));
        given(passwordEncoder.matches("Incorrecta1!", CLAVE_ENCRIPTADA)).willReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequestDTO(CORREO, "Incorrecta1!")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Credenciales invalidas");
    }

    @Test
    void logoutRevocaElTokenRecibidoYConfirmaElCierre() {
        String token = "jwt.ficticio.firmado";

        assertThat(authService.logout(token).getMessage()).isEqualTo("Sesion cerrada correctamente");

        verify(revocacionTokenService).revocar(token);
        verifyNoInteractions(usuarioRepository, passwordEncoder);
    }
}

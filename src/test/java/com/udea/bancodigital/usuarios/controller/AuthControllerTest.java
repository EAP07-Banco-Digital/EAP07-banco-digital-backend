package com.udea.bancodigital.usuarios.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.udea.bancodigital.shared.config.CustomAccessDeniedHandler;
import com.udea.bancodigital.shared.config.CustomAuthEntryPoint;
import com.udea.bancodigital.shared.jwt.JwtAuthenticationFilter;
import com.udea.bancodigital.usuarios.dto.LoginRequestDTO;
import com.udea.bancodigital.usuarios.dto.LoginResponseDTO;
import com.udea.bancodigital.usuarios.dto.LogoutResponseDTO;
import com.udea.bancodigital.usuarios.service.AuthService;
import org.assertj.core.api.Assertions;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private CustomAuthEntryPoint customAuthEntryPoint;

    @MockitoBean
    private CustomAccessDeniedHandler customAccessDeniedHandler;

    private Map<String, String> credencialesValidas() {
        Map<String, String> cuerpo = new HashMap<>();
        cuerpo.put("email", "juan@banco.com");
        cuerpo.put("password", "Segura123!");
        return cuerpo;
    }

    private org.springframework.test.web.servlet.ResultActions iniciarSesion(Map<String, String> cuerpo)
            throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    @Test
    void loginExitosoDevuelve200ConElTokenYTipoBearer() throws Exception {
        given(authService.login(any())).willReturn(new LoginResponseDTO("jwt.ficticio.firmado", "Bearer"));

        iniciarSesion(credencialesValidas())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt.ficticio.firmado"))
                .andExpect(jsonPath("$.tipo").value("Bearer"));
    }

    @Test
    void laRespuestaDelLoginNoExponeElHashDeLaContrasena() throws Exception {
        given(authService.login(any())).willReturn(new LoginResponseDTO("jwt.ficticio.firmado", "Bearer"));

        iniciarSesion(credencialesValidas())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void loginSinCorreoDevuelve400() throws Exception {
        Map<String, String> cuerpo = credencialesValidas();
        cuerpo.remove("email");

        iniciarSesion(cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("El email es obligatorio"));
    }

    @Test
    void loginConCorreoMalFormadoDevuelve400() throws Exception {
        Map<String, String> cuerpo = credencialesValidas();
        cuerpo.put("email", "esto-no-es-un-correo");

        iniciarSesion(cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El email no tiene un formato valido"));
    }

    @Test
    void loginSinContrasenaDevuelve400() throws Exception {
        Map<String, String> cuerpo = credencialesValidas();
        cuerpo.remove("password");

        iniciarSesion(cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La contraseña es obligatoria"));
    }

    @Test
    void loginConCredencialesIncorrectasDevuelve401() throws Exception {
        willThrow(new BadCredentialsException("Credenciales invalidas")).given(authService).login(any());

        iniciarSesion(credencialesValidas())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Credenciales invalidas"));
    }

    @Test
    void loginDeUnUsuarioInactivoDevuelve401() throws Exception {
        willThrow(new BadCredentialsException("El usuario no esta activo")).given(authService).login(any());

        iniciarSesion(credencialesValidas())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("El usuario no esta activo"));
    }

    @Test
    void unaExcepcionNoContempladaEnElLoginResponde500SinFiltrarDetalles() throws Exception {
        willThrow(new IllegalStateException("detalle interno")).given(authService).login(any());

        iniciarSesion(credencialesValidas())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Ocurrio un error inesperado"))
                .andExpect(jsonPath("$.message").value(Matchers.not(Matchers.containsString("detalle interno"))));
    }

    @Test
    void elServicioRecibeElCorreoYLaContrasenaTalComoVinieron() throws Exception {
        given(authService.login(any())).willReturn(new LoginResponseDTO("jwt.ficticio.firmado", "Bearer"));

        iniciarSesion(credencialesValidas());

        ArgumentCaptor<LoginRequestDTO> captor = ArgumentCaptor.forClass(LoginRequestDTO.class);
        Mockito.verify(authService).login(captor.capture());
        Assertions.assertThat(captor.getValue().getEmail()).isEqualTo("juan@banco.com");
        Assertions.assertThat(captor.getValue().getPassword()).isEqualTo("Segura123!");
    }

    @Test
    void logoutDevuelve200YLeEntregaAlServicioElTokenSinElPrefijoBearer() throws Exception {
        given(authService.logout("jwt.ficticio.firmado"))
                .willReturn(new LogoutResponseDTO("Sesion cerrada correctamente"));

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer jwt.ficticio.firmado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Sesion cerrada correctamente"));

        Mockito.verify(authService).logout("jwt.ficticio.firmado");
    }
}

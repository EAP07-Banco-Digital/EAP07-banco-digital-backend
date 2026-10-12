package com.udea.bancodigital.usuarios.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.udea.bancodigital.shared.config.CustomAuthEntryPoint;
import com.udea.bancodigital.shared.config.TraceIdFilter;
import com.udea.bancodigital.shared.exception.NegocioException;
import com.udea.bancodigital.shared.jwt.JwtAuthenticationFilter;
import com.udea.bancodigital.usuarios.dto.LoginRequestDTO;
import com.udea.bancodigital.usuarios.dto.LoginResponseDTO;
import com.udea.bancodigital.usuarios.service.AuthService;
import org.assertj.core.api.Assertions;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.hamcrest.Matchers.nullValue;
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
        given(authService.login(any())).willReturn(new LoginResponseDTO("jwt.ficticio.firmado", "Bearer", "CLIENTE"));

        iniciarSesion(credencialesValidas())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt.ficticio.firmado"))
            .andExpect(jsonPath("$.tipo").value("Bearer"))
            .andExpect(jsonPath("$.rol").value("CLIENTE"));
    }

    @Test
    void laRespuestaDelLoginNoExponeElHashDeLaContrasena() throws Exception {
        given(authService.login(any())).willReturn(new LoginResponseDTO("jwt.ficticio.firmado", "Bearer", "CLIENTE"));

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
            void quintoIntentoFallidoDevuelve403ConErrorDeCuentaBloqueada() throws Exception {
            willThrow(new NegocioException(
                "ACCOUNT_LOCKED", "Cuenta temporalmente bloqueada", HttpStatus.FORBIDDEN
            )).given(authService).login(any());

            mockMvc.perform(post("/api/auth/login")
                    .requestAttr(TraceIdFilter.TRACE_ID_ATTR, "trace-prueba")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(credencialesValidas())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.message").value("Cuenta temporalmente bloqueada"))
                .andExpect(jsonPath("$.details").value(nullValue()))
                .andExpect(jsonPath("$.traceId").value("trace-prueba"));
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
        given(authService.login(any())).willReturn(new LoginResponseDTO("jwt.ficticio.firmado", "Bearer", "CLIENTE"));

        iniciarSesion(credencialesValidas());

        ArgumentCaptor<LoginRequestDTO> captor = ArgumentCaptor.forClass(LoginRequestDTO.class);
        Mockito.verify(authService).login(captor.capture());
        Assertions.assertThat(captor.getValue().getEmail()).isEqualTo("juan@banco.com");
        Assertions.assertThat(captor.getValue().getPassword()).isEqualTo("Segura123!");
    }
}

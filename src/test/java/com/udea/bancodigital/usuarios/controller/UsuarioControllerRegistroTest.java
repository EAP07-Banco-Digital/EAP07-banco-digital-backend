package com.udea.bancodigital.usuarios.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.udea.bancodigital.shared.exception.NegocioException;
import com.udea.bancodigital.shared.config.CustomAccessDeniedHandler;
import com.udea.bancodigital.shared.config.CustomAuthEntryPoint;
import com.udea.bancodigital.shared.jwt.JwtAuthenticationFilter;
import com.udea.bancodigital.usuarios.dto.MensajesRegistro;
import com.udea.bancodigital.usuarios.dto.RegistroUsuarioResponseDTO;
import com.udea.bancodigital.usuarios.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UsuarioController.class)
@AutoConfigureMockMvc(addFilters = false)
class UsuarioControllerRegistroTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private UsuarioService usuarioService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private CustomAuthEntryPoint customAuthEntryPoint;

    @MockitoBean
    private CustomAccessDeniedHandler customAccessDeniedHandler;

    private Map<String, String> peticionValida() {
        Map<String, String> cuerpo = new HashMap<>();
        cuerpo.put("nombres", "Juan Manuel");
        cuerpo.put("apellidos", "Tabares");
        cuerpo.put("tipoDocumento", "CC");
        cuerpo.put("numeroDocumento", "1017654321");
        cuerpo.put("email", "juan@banco.com");
        cuerpo.put("fechaNacimiento", "1998-04-15");
        cuerpo.put("telefono", "3001234567");
        cuerpo.put("direccion", "Calle 10 #20-30");
        cuerpo.put("password", "Segura123!");
        cuerpo.put("rol", "CLIENTE");
        return cuerpo;
    }

    private org.springframework.test.web.servlet.ResultActions registrar(Map<String, String> cuerpo)
            throws Exception {
        return mockMvc.perform(post("/api/usuarios/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    @Test
    void criterio1_registroExitosoDevuelve201YMensajeDeExito() throws Exception {
        given(usuarioService.registrar(any())).willReturn(
                new RegistroUsuarioResponseDTO(MensajesRegistro.REGISTRO_EXITOSO, 1L, "juan@banco.com"));

        registrar(peticionValida())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("El usuario ha sido registrado con éxito."));
    }

    @Test
    void criterio2_campoObligatorioAusenteDevuelve400() throws Exception {
        Map<String, String> cuerpo = peticionValida();
        cuerpo.remove("nombres");

        registrar(cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Complete todos los campos obligatorios."));
    }

    @Test
    void criterio3_correoConFormatoInvalidoDevuelve400() throws Exception {
        Map<String, String> cuerpo = peticionValida();
        cuerpo.put("email", "esto-no-es-un-correo");

        registrar(cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ingrese un correo electrónico válido."));
    }

    @Test
    void criterio4_passwordDebilDevuelve400() throws Exception {
        Map<String, String> cuerpo = peticionValida();
        cuerpo.put("password", "abcdefgh");

        registrar(cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("La contraseña no cumple con los requisitos de seguridad."));
    }

    @Test
    void criterio5_correoDuplicadoDevuelveConflicto() throws Exception {
        willThrow(new NegocioException(
                "EMAIL_ALREADY_REGISTERED", MensajesRegistro.CORREO_DUPLICADO, HttpStatus.CONFLICT))
                .given(usuarioService).registrar(any());

        registrar(peticionValida())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El correo ingresado ya está registrado."));
    }

    @Test
    void passwordVaciaPrefiereElMensajeDeCampoObligatorio() throws Exception {
        Map<String, String> cuerpo = peticionValida();
        cuerpo.put("password", "");

        registrar(cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Complete todos los campos obligatorios."));
    }

    // ------------------------------------- errores del cliente que eran un 500

    @Test
    void unJsonMalFormadoDevuelve400MalformedRequest() throws Exception {
        mockMvc.perform(post("/api/usuarios/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombres\": \"Juan\","))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));
    }

    @Test
    void unaFechaConFormatoInvalidoDevuelve400MalformedRequest() throws Exception {
        Map<String, String> cuerpo = peticionValida();
        cuerpo.put("fechaNacimiento", "1998-13-45");

        registrar(cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));
    }

    @Test
    void unCuerpoQueNoEsJsonDevuelve415() throws Exception {
        mockMvc.perform(post("/api/usuarios/registro")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("nombres=Juan"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void unMetodoNoSoportadoDevuelve405() throws Exception {
        mockMvc.perform(get("/api/usuarios/registro"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unaRutaInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get("/api/usuarios/no-existe/en-ninguna-parte"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void unCampoMasLargoQueLaColumnaDevuelve400EnLugarDe500() throws Exception {
        Map<String, String> cuerpo = peticionValida();
        cuerpo.put("direccion", "x".repeat(256));

        registrar(cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(MensajesRegistro.CAMPO_DEMASIADO_LARGO));
    }

    // ------------------------------------------------- fecha de nacimiento

    @Test
    void unaFechaDeNacimientoFuturaDevuelve400() throws Exception {
        Map<String, String> cuerpo = peticionValida();
        cuerpo.put("fechaNacimiento", java.time.LocalDate.now().plusDays(1).toString());

        registrar(cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(MensajesRegistro.FECHA_NACIMIENTO_INVALIDA))
                .andExpect(jsonPath("$.details.fechaNacimiento").value(MensajesRegistro.FECHA_NACIMIENTO_INVALIDA));
    }

    @Test
    void laFechaDeHoyComoNacimientoTambienDevuelve400() throws Exception {
        Map<String, String> cuerpo = peticionValida();
        cuerpo.put("fechaNacimiento", java.time.LocalDate.now().toString());

        registrar(cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(MensajesRegistro.FECHA_NACIMIENTO_INVALIDA));
    }

    @Test
    void laFechaDeAyerComoNacimientoSeAcepta() throws Exception {
        given(usuarioService.registrar(any()))
                .willReturn(new RegistroUsuarioResponseDTO(MensajesRegistro.REGISTRO_EXITOSO, 1L, "juan@banco.com"));
        Map<String, String> cuerpo = peticionValida();
        cuerpo.put("fechaNacimiento", java.time.LocalDate.now().minusDays(1).toString());

        registrar(cuerpo).andExpect(status().isCreated());
    }
}

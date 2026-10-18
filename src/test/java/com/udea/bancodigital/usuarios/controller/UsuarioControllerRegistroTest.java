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
}

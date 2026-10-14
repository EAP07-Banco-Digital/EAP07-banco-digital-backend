package com.udea.bancodigital.usuarios.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.udea.bancodigital.shared.config.CustomAccessDeniedHandler;
import com.udea.bancodigital.shared.config.CustomAuthEntryPoint;
import com.udea.bancodigital.shared.exception.NegocioException;
import com.udea.bancodigital.shared.jwt.JwtAuthenticationFilter;
import com.udea.bancodigital.usuarios.dto.ActualizarPerfilRequestDTO;
import com.udea.bancodigital.usuarios.dto.MensajesPerfil;
import com.udea.bancodigital.usuarios.dto.PerfilUsuarioDTO;
import com.udea.bancodigital.usuarios.service.UsuarioService;
import org.assertj.core.api.Assertions;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Principal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UsuarioController.class)
@AutoConfigureMockMvc(addFilters = false)
class UsuarioControllerPerfilTest {

    private static final String CORREO_DEL_TOKEN = "juan@banco.com";

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

    private PerfilUsuarioDTO perfil() {
        return new PerfilUsuarioDTO(1L, "Juan Manuel Tabares", CORREO_DEL_TOKEN, "***4321",
                LocalDate.of(1998, 4, 15), "3001234567", "Calle 10 #20-30");
    }

    private Map<String, String> actualizacionValida() {
        Map<String, String> cuerpo = new HashMap<>();
        cuerpo.put("telefono", "3009999999");
        cuerpo.put("email", CORREO_DEL_TOKEN);
        cuerpo.put("direccion", "Carrera 43 #18-20");
        return cuerpo;
    }

    @Test
    void consultarPerfilTomaElCorreoDelTokenYNoDeLaPeticion() throws Exception {
        given(usuarioService.consultarPerfil(CORREO_DEL_TOKEN)).willReturn(perfil());

        mockMvc.perform(get("/api/usuarios/me").principal((Principal) () -> CORREO_DEL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nombre").value("Juan Manuel Tabares"))
                .andExpect(jsonPath("$.email").value(CORREO_DEL_TOKEN))
                .andExpect(jsonPath("$.documentoIdentidad").value("***4321"));

        verify(usuarioService).consultarPerfil(CORREO_DEL_TOKEN);
    }

    @Test
    void elPerfilNuncaDevuelveElDocumentoCompleto() throws Exception {
        given(usuarioService.consultarPerfil(CORREO_DEL_TOKEN)).willReturn(perfil());

        mockMvc.perform(get("/api/usuarios/me").principal((Principal) () -> CORREO_DEL_TOKEN))
                .andExpect(jsonPath("$.documentoIdentidad").value("***4321"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("1017654321"))));
    }

    @Test
    void consultarPerfilDeUnUsuarioInexistenteDevuelve404() throws Exception {
        given(usuarioService.consultarPerfil(CORREO_DEL_TOKEN)).willThrow(new NegocioException(
                "USER_NOT_FOUND", "El usuario autenticado ya no existe", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/usuarios/me").principal((Principal) () -> CORREO_DEL_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"));
    }

    @Test
    void actualizarPerfilTomaElCorreoDelTokenYDevuelveElPerfilActualizado() throws Exception {
        given(usuarioService.actualizarPerfil(eq(CORREO_DEL_TOKEN), any(ActualizarPerfilRequestDTO.class)))
                .willReturn(perfil());

        mockMvc.perform(put("/api/usuarios/me")
                        .principal((Principal) () -> CORREO_DEL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(actualizacionValida())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(CORREO_DEL_TOKEN));

        verify(usuarioService).actualizarPerfil(eq(CORREO_DEL_TOKEN), any(ActualizarPerfilRequestDTO.class));
    }

    @Test
    void elDocumentoDeIdentidadViajaAlServicioQueEsQuienLoRechaza() throws Exception {
        // El controlador no valida este campo: lo reenvia tal cual y es
        // UsuarioService.actualizarPerfil el que lo prohibe.
        Map<String, String> cuerpo = actualizacionValida();
        cuerpo.put("documentoIdentidad", "1017654321");
        given(usuarioService.actualizarPerfil(eq(CORREO_DEL_TOKEN), any(ActualizarPerfilRequestDTO.class)))
                .willThrow(new NegocioException("FIELD_NOT_EDITABLE",
                        MensajesPerfil.DOCUMENTO_NO_EDITABLE, HttpStatus.BAD_REQUEST));

        mockMvc.perform(put("/api/usuarios/me")
                        .principal((Principal) () -> CORREO_DEL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cuerpo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FIELD_NOT_EDITABLE"))
                .andExpect(jsonPath("$.message").value(MensajesPerfil.DOCUMENTO_NO_EDITABLE));

        ArgumentCaptor<ActualizarPerfilRequestDTO> enviado = ArgumentCaptor.forClass(ActualizarPerfilRequestDTO.class);
        verify(usuarioService).actualizarPerfil(eq(CORREO_DEL_TOKEN), enviado.capture());
        Assertions.assertThat(enviado.getValue().getDocumentoIdentidad()).isEqualTo("1017654321");
    }

    @Test
    void actualizarPerfilConCorreoDuplicadoDevuelve409() throws Exception {
        given(usuarioService.actualizarPerfil(eq(CORREO_DEL_TOKEN), any(ActualizarPerfilRequestDTO.class)))
                .willThrow(new NegocioException("DUPLICATE_EMAIL",
                        MensajesPerfil.CORREO_DE_OTRO_USUARIO, HttpStatus.CONFLICT));

        mockMvc.perform(put("/api/usuarios/me")
                        .principal((Principal) () -> CORREO_DEL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(actualizacionValida())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_EMAIL"))
                .andExpect(jsonPath("$.message").value(MensajesPerfil.CORREO_DE_OTRO_USUARIO));
    }

    @Test
    void actualizarPerfilSinTelefonoDevuelve400() throws Exception {
        Map<String, String> cuerpo = actualizacionValida();
        cuerpo.remove("telefono");

        mockMvc.perform(put("/api/usuarios/me")
                        .principal((Principal) () -> CORREO_DEL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cuerpo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(MensajesPerfil.CAMPO_VACIO));

        verify(usuarioService, never()).actualizarPerfil(any(), any());
    }

    @Test
    void actualizarPerfilConCorreoMalFormadoDevuelve400() throws Exception {
        Map<String, String> cuerpo = actualizacionValida();
        cuerpo.put("email", "esto-no-es-un-correo");

        mockMvc.perform(put("/api/usuarios/me")
                        .principal((Principal) () -> CORREO_DEL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cuerpo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ingrese un correo electrónico válido."));
    }

    @Test
    void elEndpointDePruebaRespondeConElMensajeDeVerificacion() throws Exception {
        mockMvc.perform(get("/api/usuarios/prueba").principal((Principal) () -> CORREO_DEL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().string("Usuario autenticado correctamente"));
    }
}

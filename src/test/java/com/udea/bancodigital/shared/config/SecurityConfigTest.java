package com.udea.bancodigital.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.udea.bancodigital.shared.jwt.JwtAuthenticationFilter;
import com.udea.bancodigital.shared.jwt.JwtService;
import com.udea.bancodigital.shared.jwt.RevocacionTokenService;
import com.udea.bancodigital.usuarios.api.UsuarioApi;
import com.udea.bancodigital.usuarios.controller.UsuarioController;
import com.udea.bancodigital.usuarios.dto.CambioRolResponseDTO;
import com.udea.bancodigital.usuarios.dto.PerfilUsuarioDTO;
import com.udea.bancodigital.usuarios.dto.RegistroUsuarioResponseDTO;
import com.udea.bancodigital.usuarios.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba la cadena de seguridad completa, con los filtros activos: es la unica
 * forma de verificar a quien deja pasar SecurityConfig y a quien no.
 */
@WebMvcTest(UsuarioController.class)
@Import({SecurityConfig.class, PasswordConfig.class, CustomAuthEntryPoint.class, CustomAccessDeniedHandler.class,
        JwtAuthenticationFilter.class, TraceIdFilter.class})
class SecurityConfigTest {

    private static final String CORREO = "juan@banco.com";
    private static final Long USUARIO_ID = 33L;
    private static final String TOKEN = "jwt.ficticio.firmado";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private UsuarioService usuarioService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RevocacionTokenService revocacionTokenService;

    private void tokenValidoDe(String rol) {
        given(jwtService.esTokenValido(TOKEN)).willReturn(true);
        given(jwtService.extraerUsuarioId(TOKEN)).willReturn(USUARIO_ID);
        given(usuarioService.obtenerIdentidadPorId(USUARIO_ID)).willReturn(Optional.of(new UsuarioApi.Identidad(CORREO, rol)));
    }

    private Map<String, String> registroValido() {
        Map<String, String> cuerpo = new HashMap<>();
        cuerpo.put("nombres", "Juan Manuel");
        cuerpo.put("apellidos", "Tabares");
        cuerpo.put("tipoDocumento", "CC");
        cuerpo.put("numeroDocumento", "1017654321");
        cuerpo.put("email", CORREO);
        cuerpo.put("fechaNacimiento", "1998-04-15");
        cuerpo.put("telefono", "3001234567");
        cuerpo.put("direccion", "Calle 10 #20-30");
        cuerpo.put("password", "Segura123!");
        cuerpo.put("rol", "CLIENTE");
        return cuerpo;
    }

    @Test
    void unEndpointProtegidoSinTokenDevuelve401ConElJsonUniforme() throws Exception {
        mockMvc.perform(get("/api/usuarios/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Debes iniciar sesion para acceder a este recurso"))
                .andExpect(content().string(not(containsString("traceId\":null"))));
    }

    @Test
    void laRespuestaDe401IncluyeElTraceIdParaPoderRastrearLaPeticion() throws Exception {
        String cuerpo = mockMvc.perform(get("/api/usuarios/me"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String traceId = objectMapper.readTree(cuerpo).get("traceId").asText();
        org.assertj.core.api.Assertions.assertThat(traceId).isNotBlank();
    }

    @Test
    void unTokenInvalidoTambienTerminaEn401() throws Exception {
        given(jwtService.esTokenValido("token.inventado")).willReturn(false);

        mockMvc.perform(get("/api/usuarios/me").header("Authorization", "Bearer token.inventado"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void unTokenValidoDeUnClientePermiteEntrarAlPerfil() throws Exception {
        tokenValidoDe("CLIENTE");
        given(usuarioService.consultarPerfil(CORREO))
                .willReturn(new PerfilUsuarioDTO(1L, "Juan Manuel Tabares", CORREO, "***4321",
                        LocalDate.of(1998, 4, 15), "3001234567", "Calle 10 #20-30"));

        mockMvc.perform(get("/api/usuarios/me").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(CORREO));
    }

    @Test
    void unTokenDeUnUsuarioQueYaNoExisteTerminaEn401() throws Exception {
        given(jwtService.esTokenValido(TOKEN)).willReturn(true);
        given(jwtService.extraerUsuarioId(TOKEN)).willReturn(USUARIO_ID);
        given(usuarioService.obtenerIdentidadPorId(USUARIO_ID)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/usuarios/me").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void unTokenConUnRolDistintoDeClienteTerminaEn403() throws Exception {
        tokenValidoDe("ADMIN");

        mockMvc.perform(get("/api/usuarios/me").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void el403PorRolInsuficienteUsaElJsonUniformeConForbidden() throws Exception {
        // Antes documentaba DEF-T-02 (403 sin cuerpo); la HU9 lo corrige con
        // CustomAccessDeniedHandler.
        tokenValidoDe("ADMIN");

        mockMvc.perform(get("/api/usuarios/me").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("No tienes permisos para realizar esta operacion"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void unClienteNoPuedeCambiarRolesYRecibe403Forbidden() throws Exception {
        tokenValidoDe("CLIENTE");

        mockMvc.perform(put("/api/usuarios/2/rol")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rol\":\"ADMIN\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        // El filtro si consulta el rol (UsuarioApi); lo que nunca debe correr es el cambio.
        verify(usuarioService, never()).cambiarRol(any(), any(), any());
    }

    @Test
    void unAdministradorSiPuedeLlegarAlCambioDeRol() throws Exception {
        tokenValidoDe("ADMIN");
        given(usuarioService.cambiarRol(eq(CORREO), eq(2L), any()))
                .willReturn(new CambioRolResponseDTO("El rol del usuario fue actualizado.", 2L,
                        "otro@banco.com", "ADMIN"));

        mockMvc.perform(put("/api/usuarios/2/rol")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rol\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("ADMIN"));
    }

    @Test
    void elCambioDeRolSinTokenTerminaEn401() throws Exception {
        mockMvc.perform(put("/api/usuarios/2/rol")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rol\":\"ADMIN\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void elRegistroEsPublicoYNoExigeToken() throws Exception {
        given(usuarioService.registrar(any()))
                .willReturn(new RegistroUsuarioResponseDTO("El usuario ha sido registrado con éxito.", 1L, CORREO));

        mockMvc.perform(post("/api/usuarios/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registroValido())))
                .andExpect(status().isCreated());
    }

    @Test
    void laDocumentacionDeSwaggerNoExigeToken() throws Exception {
        // El endpoint en si no existe dentro del slice, asi que lo unico que se
        // verifica aqui es la decision de seguridad: la ruta esta en permitAll
        // y por eso la peticion no se corta con 401 ni con 403.
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(result -> org.assertj.core.api.Assertions
                        .assertThat(result.getResponse().getStatus()).isNotIn(401, 403));
    }

    @Test
    void unaRutaNoPermitidaSinTokenSeCortaCon401() throws Exception {
        // anyRequest().hasRole("CLIENTE"): lo que no este en la lista de
        // permitAll exige token, aunque la ruta no exista.
        mockMvc.perform(get("/api/ruta-que-no-existe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void unTokenRevocadoTerminaEn401ConTokenRevokedYElJsonUniforme() throws Exception {
        tokenValidoDe("CLIENTE");
        given(revocacionTokenService.estaRevocado(TOKEN)).willReturn(true);

        mockMvc.perform(get("/api/usuarios/me").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("TOKEN_REVOKED"))
                .andExpect(jsonPath("$.message").value("La sesion fue cerrada. Inicia sesion de nuevo"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void unTokenRevocadoNoImpideUsarUnaRutaPublica() throws Exception {
        // Un cliente que cerro sesion puede seguir mandando el header viejo
        // por descuido: eso no debe romperle el registro ni el login.
        given(jwtService.esTokenValido(TOKEN)).willReturn(true);
        given(revocacionTokenService.estaRevocado(TOKEN)).willReturn(true);
        given(usuarioService.registrar(any()))
                .willReturn(new RegistroUsuarioResponseDTO("El usuario ha sido registrado con éxito.", 1L, CORREO));

        mockMvc.perform(post("/api/usuarios/registro")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registroValido())))
                .andExpect(status().isCreated());
    }
}

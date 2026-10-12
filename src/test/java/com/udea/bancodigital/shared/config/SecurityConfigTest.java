package com.udea.bancodigital.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.udea.bancodigital.shared.entity.Estado;
import com.udea.bancodigital.shared.jwt.JwtAuthenticationFilter;
import com.udea.bancodigital.shared.jwt.JwtService;
import com.udea.bancodigital.usuarios.controller.UsuarioController;
import com.udea.bancodigital.usuarios.dto.PerfilUsuarioDTO;
import com.udea.bancodigital.usuarios.dto.RegistroUsuarioResponseDTO;
import com.udea.bancodigital.usuarios.entity.Rol;
import com.udea.bancodigital.usuarios.entity.Usuario;
import com.udea.bancodigital.usuarios.repository.UsuarioRepository;
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
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba la cadena de seguridad completa, con los filtros activos: es la unica
 * forma de verificar a quien deja pasar SecurityConfig y a quien no.
 */
@WebMvcTest(UsuarioController.class)
@Import({SecurityConfig.class, CustomAuthEntryPoint.class, JwtAuthenticationFilter.class, TraceIdFilter.class})
class SecurityConfigTest {

    private static final String CORREO = "juan@banco.com";
    private static final String TOKEN = "jwt.ficticio.firmado";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private UsuarioService usuarioService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

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

    private void tokenValidoDe(String rol) {
        given(jwtService.esTokenValido(TOKEN)).willReturn(true);
        given(jwtService.extraerEmail(TOKEN)).willReturn(CORREO);
        given(usuarioRepository.findByEmail(CORREO)).willReturn(Optional.of(usuarioConRol(rol)));
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
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"))
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
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
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
        given(jwtService.extraerEmail(TOKEN)).willReturn(CORREO);
        given(usuarioRepository.findByEmail(CORREO)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/usuarios/me").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    void unTokenConUnRolDistintoDeClienteTerminaEn403() throws Exception {
        tokenValidoDe("ADMIN");

        mockMvc.perform(get("/api/usuarios/me").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void el403PorRolInsuficienteNoUsaElJsonUniforme() throws Exception {
        // Documenta DEF-T-02: el rechazo por rol sale con la respuesta por defecto
        // de Spring Security, sin {errorCode, message, details, traceId}.
        tokenValidoDe("ADMIN");

        String cuerpo = mockMvc.perform(get("/api/usuarios/me").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(cuerpo).doesNotContain("errorCode");
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
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }
}

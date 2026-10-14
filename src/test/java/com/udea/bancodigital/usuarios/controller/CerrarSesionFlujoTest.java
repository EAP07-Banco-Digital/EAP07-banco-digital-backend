package com.udea.bancodigital.usuarios.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.udea.bancodigital.shared.config.CustomAccessDeniedHandler;
import com.udea.bancodigital.shared.config.CustomAuthEntryPoint;
import com.udea.bancodigital.shared.config.PasswordConfig;
import com.udea.bancodigital.shared.config.SecurityConfig;
import com.udea.bancodigital.shared.config.TraceIdFilter;
import com.udea.bancodigital.shared.entity.Estado;
import com.udea.bancodigital.shared.entity.TokenRevocado;
import com.udea.bancodigital.shared.jwt.JwtAuthenticationFilter;
import com.udea.bancodigital.shared.jwt.JwtService;
import com.udea.bancodigital.shared.jwt.RevocacionTokenService;
import com.udea.bancodigital.shared.repository.TokenRevocadoRepository;
import com.udea.bancodigital.usuarios.dto.PerfilUsuarioDTO;
import com.udea.bancodigital.usuarios.entity.Rol;
import com.udea.bancodigital.usuarios.entity.Usuario;
import com.udea.bancodigital.usuarios.repository.UsuarioRepository;
import com.udea.bancodigital.usuarios.service.AuthService;
import com.udea.bancodigital.usuarios.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU8 de punta a punta sobre la cadena de seguridad real: login con un JWT
 * firmado de verdad, logout y reintento con el mismo token. Solo se reemplazan
 * los repositorios (la lista negra es un Set en memoria), asi que no necesita
 * base de datos y corre con el resto de la suite.
 */
@WebMvcTest({AuthController.class, UsuarioController.class})
@Import({SecurityConfig.class, PasswordConfig.class, CustomAuthEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class, TraceIdFilter.class,
        JwtService.class, RevocacionTokenService.class, AuthService.class})
@TestPropertySource(properties = {
        "jwt.secret=clave-de-prueba-de-64-caracteres-que-si-alcanza-para-hs512-0",
        "jwt.expiration-ms=3600000"
})
class CerrarSesionFlujoTest {

    private static final String CORREO = "juan@banco.com";
    private static final String CLAVE = "Segura123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @MockitoBean
    private UsuarioService usuarioService;

    @MockitoBean
    private TokenRevocadoRepository tokenRevocadoRepository;

    @BeforeEach
    void prepararUsuarioYListaNegra() {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setEmail(CORREO);
        usuario.setPasswordHash(passwordEncoder.encode(CLAVE));
        usuario.setRol(new Rol(1L, "CLIENTE"));
        usuario.setEstado(new Estado(1L, null, "ACTIVO"));
        given(usuarioRepository.findByEmail(CORREO)).willReturn(Optional.of(usuario));
        // El filtro JWT ya no lee el repositorio: pide el rol a UsuarioApi.
        given(usuarioService.obtenerRolPorEmail(CORREO)).willReturn(Optional.of("CLIENTE"));

        given(usuarioService.consultarPerfil(CORREO))
                .willReturn(new PerfilUsuarioDTO(1L, "Juan Manuel Tabares", CORREO, "***4321",
                        LocalDate.of(1998, 4, 15), "3001234567", "Calle 10 #20-30"));

        Set<String> listaNegra = new HashSet<>();
        given(tokenRevocadoRepository.save(any(TokenRevocado.class))).willAnswer(invocacion -> {
            TokenRevocado fila = invocacion.getArgument(0);
            listaNegra.add(fila.getTokenHash());
            return fila;
        });
        given(tokenRevocadoRepository.existsByTokenHash(anyString()))
                .willAnswer(invocacion -> listaNegra.contains(invocacion.<String>getArgument(0)));
    }

    private String iniciarSesion() throws Exception {
        String cuerpo = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", CORREO, "password", CLAVE))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cuerpo).get("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void cerrarSesionConUnTokenValidoResponde200() throws Exception {
        String token = iniciarSesion();

        mockMvc.perform(post("/api/auth/logout").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Sesion cerrada correctamente"));
    }

    @Test
    void despuesDelLogoutElMismoTokenEsRechazadoConTokenRevoked() throws Exception {
        String token = iniciarSesion();
        mockMvc.perform(get("/api/usuarios/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout").header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/usuarios/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("TOKEN_REVOKED"))
                .andExpect(jsonPath("$.message").value("La sesion fue cerrada. Inicia sesion de nuevo"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void unTokenRevocadoNoPuedeVolverACerrarSesion() throws Exception {
        String token = iniciarSesion();
        mockMvc.perform(post("/api/auth/logout").header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("TOKEN_REVOKED"));
    }

    @Test
    void cerrarSesionSinTokenResponde401Unauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void cerrarUnaSesionNoAfectaAOtraSesionDelMismoUsuario() throws Exception {
        String sesionCelular = iniciarSesion();
        String sesionPortatil = iniciarSesion();

        mockMvc.perform(post("/api/auth/logout").header("Authorization", bearer(sesionCelular)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/usuarios/me").header("Authorization", bearer(sesionPortatil)))
                .andExpect(status().isOk());
    }

    @Test
    void unAdministradorTambienPuedeCerrarSesion() throws Exception {
        String correoAdmin = "admin@banco.com";
        Usuario administrador = new Usuario();
        administrador.setId(2L);
        administrador.setEmail(correoAdmin);
        administrador.setPasswordHash(passwordEncoder.encode(CLAVE));
        administrador.setRol(new Rol(2L, "ADMIN"));
        administrador.setEstado(new Estado(1L, null, "ACTIVO"));
        given(usuarioRepository.findByEmail(correoAdmin)).willReturn(Optional.of(administrador));
        given(usuarioService.obtenerRolPorEmail(correoAdmin)).willReturn(Optional.of("ADMIN"));

        String cuerpo = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", correoAdmin, "password", CLAVE))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(cuerpo).get("token").asText();

        mockMvc.perform(post("/api/auth/logout").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Sesion cerrada correctamente"));

        mockMvc.perform(post("/api/auth/logout").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("TOKEN_REVOKED"));
    }

    @Test
    void trasCerrarSesionSePuedeVolverAIniciarSesionYUsarElNuevoToken() throws Exception {
        String viejo = iniciarSesion();
        mockMvc.perform(post("/api/auth/logout").header("Authorization", bearer(viejo)))
                .andExpect(status().isOk());

        String nuevo = iniciarSesion();

        mockMvc.perform(get("/api/usuarios/me").header("Authorization", bearer(nuevo)))
                .andExpect(status().isOk());
    }
}

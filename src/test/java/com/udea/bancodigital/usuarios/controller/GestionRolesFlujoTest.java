package com.udea.bancodigital.usuarios.controller;

import com.udea.bancodigital.shared.config.CustomAccessDeniedHandler;
import com.udea.bancodigital.shared.config.CustomAuthEntryPoint;
import com.udea.bancodigital.shared.config.PasswordConfig;
import com.udea.bancodigital.shared.config.SecurityConfig;
import com.udea.bancodigital.shared.config.TraceIdFilter;
import com.udea.bancodigital.shared.entity.Estado;
import com.udea.bancodigital.shared.jwt.JwtAuthenticationFilter;
import com.udea.bancodigital.shared.jwt.JwtService;
import com.udea.bancodigital.shared.jwt.RevocacionTokenService;
import com.udea.bancodigital.shared.repository.EstadoRepository;
import com.udea.bancodigital.shared.repository.TokenRevocadoRepository;
import com.udea.bancodigital.usuarios.entity.Cliente;
import com.udea.bancodigital.usuarios.entity.Rol;
import com.udea.bancodigital.usuarios.entity.Usuario;
import com.udea.bancodigital.usuarios.mapper.ClienteMapperImpl;
import com.udea.bancodigital.usuarios.mapper.UsuarioMapperImpl;
import com.udea.bancodigital.usuarios.repository.ClienteRepository;
import com.udea.bancodigital.usuarios.repository.RolRepository;
import com.udea.bancodigital.usuarios.repository.UsuarioRepository;
import com.udea.bancodigital.usuarios.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU9 de punta a punta sobre la cadena de seguridad real: JWT firmados de
 * verdad, SecurityConfig, los handlers de 401/403 y el UsuarioService real.
 * Solo se reemplazan los repositorios, asi que no necesita base de datos y
 * corre con el resto de la suite.
 */
@WebMvcTest(UsuarioController.class)
@Import({SecurityConfig.class, PasswordConfig.class, CustomAuthEntryPoint.class, CustomAccessDeniedHandler.class,
        JwtAuthenticationFilter.class, TraceIdFilter.class, JwtService.class, RevocacionTokenService.class,
        UsuarioService.class, UsuarioMapperImpl.class, ClienteMapperImpl.class})
@TestPropertySource(properties = {
        "jwt.secret=clave-de-prueba-de-64-caracteres-que-si-alcanza-para-hs512-0",
        "jwt.expiration-ms=3600000"
})
class GestionRolesFlujoTest {

    private static final Long ADMIN_ID = 1L;
    private static final String CORREO_ADMIN = "admin@banco.com";
    private static final Long CLIENTE_ID = 2L;
    private static final String CORREO_CLIENTE = "cliente@banco.com";

    private static final Rol ROL_ADMIN = new Rol(1L, "ADMIN");
    private static final Rol ROL_CLIENTE = new Rol(2L, "CLIENTE");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @MockitoBean
    private ClienteRepository clienteRepository;

    @MockitoBean
    private RolRepository rolRepository;

    @MockitoBean
    private EstadoRepository estadoRepository;

    @MockitoBean
    private TokenRevocadoRepository tokenRevocadoRepository;

    private Usuario administrador;
    private Usuario cliente;

    private Usuario usuario(Long id, String correo, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setEmail(correo);
        usuario.setRol(rol);
        usuario.setEstado(new Estado(1L, null, "ACTIVO"));
        return usuario;
    }

    @BeforeEach
    void prepararUsuariosYRoles() {
        // Instancias nuevas en cada prueba: el servicio muta el rol del usuario.
        administrador = usuario(ADMIN_ID, CORREO_ADMIN, ROL_ADMIN);
        cliente = usuario(CLIENTE_ID, CORREO_CLIENTE, ROL_CLIENTE);

        given(usuarioRepository.findByEmail(CORREO_ADMIN)).willReturn(Optional.of(administrador));
        given(usuarioRepository.findByEmail(CORREO_CLIENTE)).willReturn(Optional.of(cliente));
        given(usuarioRepository.findById(ADMIN_ID)).willReturn(Optional.of(administrador));
        given(usuarioRepository.findById(CLIENTE_ID)).willReturn(Optional.of(cliente));
        given(usuarioRepository.findById(404L)).willReturn(Optional.empty());

        given(rolRepository.findByNombreIgnoreCase("ADMIN")).willReturn(Optional.of(ROL_ADMIN));
        given(rolRepository.findByNombreIgnoreCase("CLIENTE")).willReturn(Optional.of(ROL_CLIENTE));
        given(rolRepository.findByNombreIgnoreCase("SUPERUSUARIO")).willReturn(Optional.empty());
    }

    private Cliente clienteDelBanco(Long id) {
        Cliente datos = new Cliente();
        datos.setId(id);
        datos.setNombres("Ana");
        datos.setApellidos("Restrepo");
        datos.setTipoDocumento("CC");
        datos.setNumeroDocumento("1017000000");
        datos.setEmail(CORREO_CLIENTE);
        datos.setTelefono("3000000000");
        datos.setDireccion("Calle 1");
        return datos;
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + jwtService.generarToken(usuario.getId(), usuario.getRol().getNombre());
    }

    private ResultActions cambiarRol(String autorizacion, Object id, String cuerpo) throws Exception {
        var peticion = put("/api/usuarios/{id}/rol", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
        if (autorizacion != null) {
            peticion.header("Authorization", autorizacion);
        }
        return mockMvc.perform(peticion);
    }

    // ----------------------------------------------- Escenario 1: cambio exitoso

    @Test
    void unAdministradorModificaElRolDeOtroUsuarioYRecibe200() throws Exception {
        cambiarRol(bearer(administrador), CLIENTE_ID, "{\"rol\":\"ADMIN\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("El rol del usuario fue actualizado."))
                .andExpect(jsonPath("$.usuarioId").value(CLIENTE_ID))
                .andExpect(jsonPath("$.email").value(CORREO_CLIENTE))
                .andExpect(jsonPath("$.rol").value("ADMIN"));

        verify(usuarioRepository).save(cliente);
        assertThat(cliente.getRol()).isEqualTo(ROL_ADMIN);
    }

    @Test
    void elNuevoRolAplicaDeInmediatoSobreElTokenQueElUsuarioYaTenia() throws Exception {
        cliente.setClienteId(20L);
        given(clienteRepository.findById(20L)).willReturn(Optional.of(clienteDelBanco(20L)));
        String tokenDelCliente = bearer(cliente);
        mockMvc.perform(get("/api/usuarios/me").header("Authorization", tokenDelCliente))
                .andExpect(status().isOk());

        cambiarRol(bearer(administrador), CLIENTE_ID, "{\"rol\":\"ADMIN\"}")
                .andExpect(status().isOk());

        // Mismo token, pero el filtro lee el rol de la base: ya no es CLIENTE.
        mockMvc.perform(get("/api/usuarios/me").header("Authorization", tokenDelCliente))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    // ------------------------------------- Escenario 2: cliente sin permisos

    @Test
    void unClienteQueIntentaModificarElRolDeOtroRecibe403Forbidden() throws Exception {
        cambiarRol(bearer(cliente), ADMIN_ID, "{\"rol\":\"CLIENTE\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("No tienes permisos para realizar esta operacion"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        verify(usuarioRepository, never()).save(any());
        assertThat(administrador.getRol()).isEqualTo(ROL_ADMIN);
    }

    @Test
    void unClienteTampocoPuedeAscenderseAAdministrador() throws Exception {
        cambiarRol(bearer(cliente), CLIENTE_ID, "{\"rol\":\"ADMIN\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        verify(usuarioRepository, never()).save(any());
    }

    // --------------------------------------- Escenario 3: cambio del propio rol

    @Test
    void unAdministradorQueIntentaModificarSuPropioRolRecibe400() throws Exception {
        cambiarRol(bearer(administrador), ADMIN_ID, "{\"rol\":\"CLIENTE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SELF_ROLE_CHANGE_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").value("No puedes modificar tu propio rol."))
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        verify(usuarioRepository, never()).save(any());
        assertThat(administrador.getRol()).isEqualTo(ROL_ADMIN);
    }

    // ------------------------------- Escenario 4: endpoint de un rol distinto

    @Test
    void unAdministradorEnUnEndpointReservadoAClientesRecibe403Forbidden() throws Exception {
        mockMvc.perform(get("/api/usuarios/me").header("Authorization", bearer(administrador)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    // ----------------------------------------------------- Casos complementarios

    @Test
    void cambiarRolSinTokenRecibe401() throws Exception {
        cambiarRol(null, CLIENTE_ID, "{\"rol\":\"ADMIN\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void cambiarARolInexistenteRecibe400InvalidRole() throws Exception {
        cambiarRol(bearer(administrador), CLIENTE_ID, "{\"rol\":\"SUPERUSUARIO\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ROLE"));

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cambiarRolSinIndicarElRolRecibe400ValidationError() throws Exception {
        cambiarRol(bearer(administrador), CLIENTE_ID, "{\"rol\":\"  \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Debe indicar el nuevo rol."));
    }

    @Test
    void cambiarRolDeUnUsuarioInexistenteRecibe404() throws Exception {
        cambiarRol(bearer(administrador), 404L, "{\"rol\":\"ADMIN\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"));
    }

    @Test
    void unIdNoNumericoRecibe400YNoUn500() throws Exception {
        cambiarRol(bearer(administrador), "abc", "{\"rol\":\"ADMIN\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }
}

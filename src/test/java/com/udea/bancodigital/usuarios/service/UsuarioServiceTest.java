package com.udea.bancodigital.usuarios.service;

import com.udea.bancodigital.shared.entity.Estado;
import com.udea.bancodigital.shared.repository.EstadoRepository;
import com.udea.bancodigital.usuarios.dto.ActualizarPerfilRequestDTO;
import com.udea.bancodigital.usuarios.dto.CambiarRolRequestDTO;
import com.udea.bancodigital.usuarios.dto.CambioRolResponseDTO;
import com.udea.bancodigital.usuarios.dto.MensajesPerfil;
import com.udea.bancodigital.usuarios.dto.MensajesRegistro;
import com.udea.bancodigital.usuarios.dto.MensajesRol;
import com.udea.bancodigital.usuarios.dto.PerfilUsuarioDTO;
import com.udea.bancodigital.usuarios.dto.RegistroUsuarioRequestDTO;
import com.udea.bancodigital.usuarios.dto.RegistroUsuarioResponseDTO;
import com.udea.bancodigital.usuarios.entity.Cliente;
import com.udea.bancodigital.usuarios.entity.Rol;
import com.udea.bancodigital.usuarios.entity.Usuario;
import com.udea.bancodigital.usuarios.mapper.ClienteMapper;
import com.udea.bancodigital.usuarios.mapper.UsuarioMapper;
import com.udea.bancodigital.usuarios.repository.ClienteRepository;
import com.udea.bancodigital.usuarios.repository.RolRepository;
import com.udea.bancodigital.usuarios.repository.UsuarioRepository;
import com.udea.bancodigital.shared.exception.NegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    private static final Long CLIENTE_ID = 10L;
    private static final Long USUARIO_ID = 1L;
    private static final String CORREO = "juan@banco.com";
    private static final String HASH = "$2a$10$hashFicticioDePrueba";

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private RolRepository rolRepository;

    @Mock
    private EstadoRepository estadoRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    // Los mappers reales de MapStruct: aqui no interesa simular el mapeo, sino
    // comprobar que el servicio guarda y devuelve exactamente lo que ellos producen.
    @Spy
    private ClienteMapper clienteMapper = ClienteMapper.INSTANCE;

    @Spy
    private UsuarioMapper usuarioMapper = UsuarioMapper.INSTANCE;

    @InjectMocks
    private UsuarioService usuarioService;

    private RegistroUsuarioRequestDTO solicitudValida() {
        return new RegistroUsuarioRequestDTO(
                "Juan Manuel", "Tabares", "CC", "1017654321", CORREO,
                LocalDate.of(1998, 4, 15), "3001234567", "Calle 10 #20-30",
                "Segura123!", "CLIENTE");
    }

    private Cliente clientePersistido() {
        Cliente cliente = new Cliente();
        cliente.setId(CLIENTE_ID);
        cliente.setNombres("Juan Manuel");
        cliente.setApellidos("Tabares");
        cliente.setTipoDocumento("CC");
        cliente.setNumeroDocumento("1017654321");
        cliente.setEmail(CORREO);
        cliente.setTelefono("3001234567");
        cliente.setDireccion("Calle 10 #20-30");
        cliente.setFechaRegistro(OffsetDateTime.now());
        return cliente;
    }

    private Usuario usuarioPersistido() {
        Usuario usuario = new Usuario();
        usuario.setId(USUARIO_ID);
        usuario.setEmail(CORREO);
        usuario.setPasswordHash(HASH);
        usuario.setFechaNacimiento(LocalDate.of(1998, 4, 15));
        usuario.setClienteId(CLIENTE_ID);
        usuario.setRol(new Rol(1L, "CLIENTE"));
        usuario.setEstado(new Estado(1L, null, "ACTIVO"));
        return usuario;
    }

    // ---------------------------------------------------------------- registro

    @Test
    void registroExitosoCreaClienteYUsuarioYDevuelveElId() {
        Usuario usuarioGuardado = usuarioPersistido();
        given(rolRepository.findByNombreIgnoreCase("CLIENTE")).willReturn(Optional.of(new Rol(1L, "CLIENTE")));
        given(estadoRepository.findByNombreIgnoreCase("ACTIVO")).willReturn(Optional.of(new Estado(1L, null, "ACTIVO")));
        given(passwordEncoder.encode("Segura123!")).willReturn(HASH);
        given(clienteRepository.save(any(Cliente.class))).willAnswer(inv -> {
            Cliente cliente = inv.getArgument(0);
            cliente.setId(CLIENTE_ID);
            return cliente;
        });
        given(usuarioRepository.save(any(Usuario.class))).willReturn(usuarioGuardado);

        RegistroUsuarioResponseDTO respuesta = usuarioService.registrar(solicitudValida());

        assertThat(respuesta.getMessage()).isEqualTo(MensajesRegistro.REGISTRO_EXITOSO);
        assertThat(respuesta.getUsuarioId()).isEqualTo(USUARIO_ID);
        assertThat(respuesta.getEmail()).isEqualTo(CORREO);
    }

    @Test
    void registroGuardaElClienteAntesDeCrearElUsuario() {
        given(rolRepository.findByNombreIgnoreCase("CLIENTE")).willReturn(Optional.of(new Rol(1L, "CLIENTE")));
        given(estadoRepository.findByNombreIgnoreCase("ACTIVO")).willReturn(Optional.of(new Estado(1L, null, "ACTIVO")));
        given(passwordEncoder.encode(anyString())).willReturn(HASH);
        given(clienteRepository.save(any(Cliente.class))).willAnswer(inv -> {
            Cliente cliente = inv.getArgument(0);
            cliente.setId(CLIENTE_ID);
            return cliente;
        });
        given(usuarioRepository.save(any(Usuario.class))).willReturn(usuarioPersistido());

        usuarioService.registrar(solicitudValida());

        ArgumentCaptor<Usuario> usuario = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(usuario.capture());
        // El usuario queda amarrado al cliente recien creado: es lo que permite
        // resolver el titular desde el token.
        assertThat(usuario.getValue().getClienteId()).isEqualTo(CLIENTE_ID);
    }

    @Test
    void registroNuncaGuardaLaContrasenaEnClaro() {
        given(rolRepository.findByNombreIgnoreCase("CLIENTE")).willReturn(Optional.of(new Rol(1L, "CLIENTE")));
        given(estadoRepository.findByNombreIgnoreCase("ACTIVO")).willReturn(Optional.of(new Estado(1L, null, "ACTIVO")));
        given(passwordEncoder.encode("Segura123!")).willReturn(HASH);
        given(clienteRepository.save(any(Cliente.class))).willAnswer(inv -> {
            Cliente cliente = inv.getArgument(0);
            cliente.setId(CLIENTE_ID);
            return cliente;
        });

        ArgumentCaptor<Usuario> usuario = ArgumentCaptor.forClass(Usuario.class);
        given(usuarioRepository.save(usuario.capture())).willAnswer(inv -> {
            Usuario guardado = inv.getArgument(0);
            guardado.setId(USUARIO_ID);
            return guardado;
        });

        usuarioService.registrar(solicitudValida());

        assertThat(usuario.getValue().getPasswordHash()).isEqualTo(HASH);
        assertThat(usuario.getValue().getPasswordHash()).isNotEqualTo("Segura123!");
    }

    @Test
    void registroNormalizaElCorreoQuitandoLosEspacios() {
        RegistroUsuarioRequestDTO solicitud = solicitudValida();
        solicitud.setEmail("  juan@banco.com  ");

        given(rolRepository.findByNombreIgnoreCase("CLIENTE")).willReturn(Optional.of(new Rol(1L, "CLIENTE")));
        given(estadoRepository.findByNombreIgnoreCase("ACTIVO")).willReturn(Optional.of(new Estado(1L, null, "ACTIVO")));
        given(passwordEncoder.encode(anyString())).willReturn(HASH);
        given(clienteRepository.save(any(Cliente.class))).willAnswer(inv -> {
            Cliente cliente = inv.getArgument(0);
            cliente.setId(CLIENTE_ID);
            return cliente;
        });

        ArgumentCaptor<Usuario> usuario = ArgumentCaptor.forClass(Usuario.class);
        given(usuarioRepository.save(usuario.capture())).willReturn(usuarioPersistido());

        usuarioService.registrar(solicitud);

        // La misma clave normalizada debe servir para detectar el duplicado en BD.
        verify(usuarioRepository).existsByEmailIgnoreCase(CORREO);
        assertThat(usuario.getValue().getEmail()).isEqualTo(CORREO);
    }

    @Test
    void registroConCorreoDuplicadoFallaYNoEscribeNada() {
        given(usuarioRepository.existsByEmailIgnoreCase(CORREO)).willReturn(true);

        assertThatThrownBy(() -> usuarioService.registrar(solicitudValida()))
                .isInstanceOfSatisfying(NegocioException.class, excepcion -> {
                    assertThat(excepcion.getErrorCode()).isEqualTo("EMAIL_ALREADY_REGISTERED");
                    assertThat(excepcion.getMessage()).isEqualTo(MensajesRegistro.CORREO_DUPLICADO);
                    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });

        verifyNoInteractions(clienteRepository, rolRepository, estadoRepository, passwordEncoder);
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void registroConRolInexistenteFallaCon400() {
        given(rolRepository.findByNombreIgnoreCase("PAGADOR")).willReturn(Optional.empty());

        assertThatThrownBy(() -> {
            RegistroUsuarioRequestDTO solicitud = solicitudValida();
            solicitud.setRol("PAGADOR");
            usuarioService.registrar(solicitud);
        })
                .isInstanceOfSatisfying(NegocioException.class, excepcion -> {
                    assertThat(excepcion.getErrorCode()).isEqualTo("INVALID_ROLE");
                    assertThat(excepcion.getMessage()).contains("PAGADOR");
                    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });

        verifyNoInteractions(clienteRepository, estadoRepository);
    }

    @Test
    void registroConRolAdminFallaCon403YNoCreaNada() {
        given(rolRepository.findByNombreIgnoreCase("ADMIN")).willReturn(Optional.of(new Rol(3L, "ADMIN")));

        assertThatThrownBy(() -> {
            RegistroUsuarioRequestDTO solicitud = solicitudValida();
            solicitud.setRol("ADMIN");
            usuarioService.registrar(solicitud);
        })
                .isInstanceOfSatisfying(NegocioException.class, excepcion -> {
                    assertThat(excepcion.getErrorCode()).isEqualTo("ROLE_NOT_ALLOWED");
                    assertThat(excepcion.getMessage()).isEqualTo(MensajesRegistro.ROL_NO_PERMITIDO);
                    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });

        verifyNoInteractions(clienteRepository, estadoRepository);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void registroConRolAdminEnMinusculasTambienSeRechaza() {
        given(rolRepository.findByNombreIgnoreCase("admin")).willReturn(Optional.of(new Rol(3L, "ADMIN")));

        assertThatThrownBy(() -> {
            RegistroUsuarioRequestDTO solicitud = solicitudValida();
            solicitud.setRol(" admin ");
            usuarioService.registrar(solicitud);
        })
                .isInstanceOfSatisfying(NegocioException.class, excepcion ->
                        assertThat(excepcion.getErrorCode()).isEqualTo("ROLE_NOT_ALLOWED"));

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void registroSinElEstadoActivoEnCatalogoFallaCon500() {
        given(rolRepository.findByNombreIgnoreCase("CLIENTE")).willReturn(Optional.of(new Rol(1L, "CLIENTE")));
        given(estadoRepository.findByNombreIgnoreCase("ACTIVO")).willReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.registrar(solicitudValida()))
                .isInstanceOfSatisfying(NegocioException.class, excepcion -> {
                    assertThat(excepcion.getErrorCode()).isEqualTo("INTERNAL_ERROR");
                    assertThat(excepcion.getMessage()).contains("ACTIVO");
                    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                });

        verifyNoInteractions(clienteRepository);
    }

    // ------------------------------------------------------- consulta de perfil

    @Test
    void consultarPerfilDevuelveLosDatosDelUsuarioYSuCliente() {
        given(usuarioRepository.findByEmail(CORREO)).willReturn(Optional.of(usuarioPersistido()));
        given(clienteRepository.findById(CLIENTE_ID)).willReturn(Optional.of(clientePersistido()));

        PerfilUsuarioDTO perfil = usuarioService.consultarPerfil(CORREO);

        assertThat(perfil.getId()).isEqualTo(USUARIO_ID);
        assertThat(perfil.getNombre()).isEqualTo("Juan Manuel Tabares");
        assertThat(perfil.getEmail()).isEqualTo(CORREO);
        assertThat(perfil.getDocumentoIdentidad()).isEqualTo("***4321");
        assertThat(perfil.getTelefono()).isEqualTo("3001234567");
    }

    @Test
    void consultarPerfilDeUnUsuarioQueYaNoExisteFallaCon404() {
        given(usuarioRepository.findByEmail(CORREO)).willReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.consultarPerfil(CORREO))
                .isInstanceOfSatisfying(NegocioException.class, excepcion -> {
                    assertThat(excepcion.getErrorCode()).isEqualTo("USER_NOT_FOUND");
                    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    void consultarPerfilDeUnUsuarioSinClienteAsociadoFallaCon500() {
        Usuario usuario = usuarioPersistido();
        usuario.setClienteId(99L);
        given(usuarioRepository.findByEmail(CORREO)).willReturn(Optional.of(usuario));
        given(clienteRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.consultarPerfil(CORREO))
                .isInstanceOfSatisfying(NegocioException.class, excepcion -> {
                    assertThat(excepcion.getErrorCode()).isEqualTo("CLIENT_NOT_FOUND");
                    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                });
    }

    // --------------------------------------------------- actualizacion de perfil

    @Test
    void actualizarPerfilCambiaCorreoTelefonoYDireccionEnLasDosTablas() {
        given(usuarioRepository.findByEmail(CORREO)).willReturn(Optional.of(usuarioPersistido()));
        given(clienteRepository.findById(CLIENTE_ID)).willReturn(Optional.of(clientePersistido()));

        PerfilUsuarioDTO perfil = usuarioService.actualizarPerfil(
                CORREO, new ActualizarPerfilRequestDTO("3009999999", "juan.nuevo@banco.com", "Carrera 43 #18-20", null));

        ArgumentCaptor<Usuario> usuario = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(usuario.capture());
        ArgumentCaptor<Cliente> cliente = ArgumentCaptor.forClass(Cliente.class);
        verify(clienteRepository).save(cliente.capture());

        // Usuario y Cliente guardan una copia del correo: si se actualizara solo
        // una, el login y el perfil dejarian de coincidir.
        assertThat(usuario.getValue().getEmail()).isEqualTo("juan.nuevo@banco.com");
        assertThat(cliente.getValue().getEmail()).isEqualTo("juan.nuevo@banco.com");
        assertThat(cliente.getValue().getTelefono()).isEqualTo("3009999999");
        assertThat(cliente.getValue().getDireccion()).isEqualTo("Carrera 43 #18-20");
        assertThat(perfil.getEmail()).isEqualTo("juan.nuevo@banco.com");
    }

    @Test
    void actualizarPerfilIgnoraLosEspaciosEnLosCamposDeTexto() {
        given(usuarioRepository.findByEmail(CORREO)).willReturn(Optional.of(usuarioPersistido()));
        given(clienteRepository.findById(CLIENTE_ID)).willReturn(Optional.of(clientePersistido()));

        usuarioService.actualizarPerfil(
                CORREO, new ActualizarPerfilRequestDTO(" 3009999999 ", " juan@banco.com ", " Calle 10 ", null));

        ArgumentCaptor<Cliente> cliente = ArgumentCaptor.forClass(Cliente.class);
        verify(clienteRepository).save(cliente.capture());

        assertThat(cliente.getValue().getTelefono()).isEqualTo("3009999999");
        assertThat(cliente.getValue().getDireccion()).isEqualTo("Calle 10");
        verify(usuarioRepository).existsByEmailIgnoreCaseAndIdNot(CORREO, USUARIO_ID);
    }

    @Test
    void actualizarPerfilRechazaElDocumentoDeIdentidad() {
        // El documento no es editable: basta con que llegue con algun valor.
        assertThatThrownBy(() -> usuarioService.actualizarPerfil(
                CORREO, new ActualizarPerfilRequestDTO("3009999999", CORREO, "Calle 10", "1017654321")))
                .isInstanceOfSatisfying(NegocioException.class, excepcion -> {
                    assertThat(excepcion.getErrorCode()).isEqualTo("FIELD_NOT_EDITABLE");
                    assertThat(excepcion.getMessage()).isEqualTo(MensajesPerfil.DOCUMENTO_NO_EDITABLE);
                    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });

        verifyNoInteractions(usuarioRepository, clienteRepository);
    }

    @Test
    void actualizarPerfilConCorreoDeOtroUsuarioFallaCon409() {
        given(usuarioRepository.findByEmail(CORREO)).willReturn(Optional.of(usuarioPersistido()));
        given(clienteRepository.findById(CLIENTE_ID)).willReturn(Optional.of(clientePersistido()));
        given(usuarioRepository.existsByEmailIgnoreCaseAndIdNot("ana@banco.com", USUARIO_ID)).willReturn(true);

        assertThatThrownBy(() -> usuarioService.actualizarPerfil(
                CORREO, new ActualizarPerfilRequestDTO("3009999999", "ana@banco.com", "Calle 10", null)))
                .isInstanceOfSatisfying(NegocioException.class, excepcion -> {
                    assertThat(excepcion.getErrorCode()).isEqualTo("DUPLICATE_EMAIL");
                    assertThat(excepcion.getMessage()).isEqualTo(MensajesPerfil.CORREO_DE_OTRO_USUARIO);
                    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });

        verify(usuarioRepository, never()).save(any(Usuario.class));
        verify(clienteRepository, never()).save(any(Cliente.class));
    }

    @Test
    void actualizarPerfilDeUnUsuarioInexistenteFallaCon404() {
        given(usuarioRepository.findByEmail(CORREO)).willReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.actualizarPerfil(
                CORREO, new ActualizarPerfilRequestDTO("3009999999", CORREO, "Calle 10", null)))
                .isInstanceOfSatisfying(NegocioException.class, excepcion ->
                        assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ------------------------------------------- API publica para otros modulos

    @Test
    void obtenerIdClientePorEmailDevuelveElClienteAsociado() {
        given(usuarioRepository.findByEmail(CORREO)).willReturn(Optional.of(usuarioPersistido()));

        assertThat(usuarioService.obtenerIdClientePorEmail(CORREO)).isEqualTo(CLIENTE_ID);
    }

    @Test
    void obtenerIdClientePorEmailDeUnUsuarioInexistenteFallaCon404() {
        given(usuarioRepository.findByEmail(eq("nadie@banco.com"))).willReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.obtenerIdClientePorEmail("nadie@banco.com"))
                .isInstanceOfSatisfying(NegocioException.class, excepcion ->
                        assertThat(excepcion.getErrorCode()).isEqualTo("USER_NOT_FOUND"));
    }

    @Test
    void obtenerRolPorEmailDevuelveElNombreDelRolActual() {
        given(usuarioRepository.findByEmail(CORREO)).willReturn(Optional.of(usuarioPersistido()));

        assertThat(usuarioService.obtenerRolPorEmail(CORREO)).contains("CLIENTE");
    }

    @Test
    void obtenerRolPorEmailDeUnUsuarioInexistenteDevuelveVacioSinFallar() {
        // El filtro JWT interpreta el vacio como "no autenticar" (401); una
        // excepcion aqui se convertiria en un 500 en medio de la cadena de filtros.
        given(usuarioRepository.findByEmail("nadie@banco.com")).willReturn(Optional.empty());

        assertThat(usuarioService.obtenerRolPorEmail("nadie@banco.com")).isEmpty();
    }

    // -------------------------------------------------------------- cambio de rol

    private static final Long ADMIN_ID = 99L;
    private static final String CORREO_ADMIN = "admin@banco.com";

    private Usuario administradorPersistido() {
        Usuario admin = new Usuario();
        admin.setId(ADMIN_ID);
        admin.setEmail(CORREO_ADMIN);
        admin.setRol(new Rol(2L, "ADMIN"));
        return admin;
    }

    @Test
    void cambiarRolAsignaElNuevoRolAlUsuarioYLoGuarda() {
        Usuario usuario = usuarioPersistido();
        given(usuarioRepository.findByEmail(CORREO_ADMIN)).willReturn(Optional.of(administradorPersistido()));
        given(rolRepository.findByNombreIgnoreCase("ADMIN")).willReturn(Optional.of(new Rol(2L, "ADMIN")));
        given(usuarioRepository.findById(USUARIO_ID)).willReturn(Optional.of(usuario));

        CambioRolResponseDTO respuesta =
                usuarioService.cambiarRol(CORREO_ADMIN, USUARIO_ID, new CambiarRolRequestDTO("ADMIN"));

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(guardado.capture());
        assertThat(guardado.getValue().getRol().getNombre()).isEqualTo("ADMIN");

        assertThat(respuesta.getMessage()).isEqualTo(MensajesRol.ROL_ACTUALIZADO);
        assertThat(respuesta.getUsuarioId()).isEqualTo(USUARIO_ID);
        assertThat(respuesta.getEmail()).isEqualTo(CORREO);
        assertThat(respuesta.getRol()).isEqualTo("ADMIN");
    }

    @Test
    void cambiarRolAceptaElNombreDelRolConEspaciosYEnMinusculas() {
        given(usuarioRepository.findByEmail(CORREO_ADMIN)).willReturn(Optional.of(administradorPersistido()));
        given(rolRepository.findByNombreIgnoreCase("admin")).willReturn(Optional.of(new Rol(2L, "ADMIN")));
        given(usuarioRepository.findById(USUARIO_ID)).willReturn(Optional.of(usuarioPersistido()));

        CambioRolResponseDTO respuesta =
                usuarioService.cambiarRol(CORREO_ADMIN, USUARIO_ID, new CambiarRolRequestDTO("  admin "));

        assertThat(respuesta.getRol()).isEqualTo("ADMIN");
    }

    @Test
    void cambiarElPropioRolFallaCon400YNoTocaNada() {
        given(usuarioRepository.findByEmail(CORREO_ADMIN)).willReturn(Optional.of(administradorPersistido()));

        assertThatThrownBy(() ->
                usuarioService.cambiarRol(CORREO_ADMIN, ADMIN_ID, new CambiarRolRequestDTO("CLIENTE")))
                .isInstanceOfSatisfying(NegocioException.class, excepcion -> {
                    assertThat(excepcion.getErrorCode()).isEqualTo("SELF_ROLE_CHANGE_NOT_ALLOWED");
                    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(excepcion.getMessage()).isEqualTo(MensajesRol.CAMBIO_PROPIO_ROL);
                });

        verifyNoInteractions(rolRepository);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cambiarRolAUnRolInexistenteFallaCon400() {
        given(usuarioRepository.findByEmail(CORREO_ADMIN)).willReturn(Optional.of(administradorPersistido()));
        given(rolRepository.findByNombreIgnoreCase("SUPERUSUARIO")).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                usuarioService.cambiarRol(CORREO_ADMIN, USUARIO_ID, new CambiarRolRequestDTO("SUPERUSUARIO")))
                .isInstanceOfSatisfying(NegocioException.class, excepcion -> {
                    assertThat(excepcion.getErrorCode()).isEqualTo("INVALID_ROLE");
                    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cambiarRolDeUnUsuarioInexistenteFallaCon404() {
        given(usuarioRepository.findByEmail(CORREO_ADMIN)).willReturn(Optional.of(administradorPersistido()));
        given(rolRepository.findByNombreIgnoreCase("ADMIN")).willReturn(Optional.of(new Rol(2L, "ADMIN")));
        given(usuarioRepository.findById(404L)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                usuarioService.cambiarRol(CORREO_ADMIN, 404L, new CambiarRolRequestDTO("ADMIN")))
                .isInstanceOfSatisfying(NegocioException.class, excepcion -> {
                    assertThat(excepcion.getErrorCode()).isEqualTo("USER_NOT_FOUND");
                    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(excepcion.getMessage()).isEqualTo(MensajesRol.USUARIO_NO_ENCONTRADO);
                });

        verify(usuarioRepository, never()).save(any());
    }
}

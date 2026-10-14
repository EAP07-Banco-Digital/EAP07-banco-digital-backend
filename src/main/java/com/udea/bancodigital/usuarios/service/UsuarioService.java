package com.udea.bancodigital.usuarios.service;

import com.udea.bancodigital.shared.exception.NegocioException;
import com.udea.bancodigital.usuarios.api.UsuarioApi;
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
import com.udea.bancodigital.shared.entity.Estado;
import com.udea.bancodigital.usuarios.entity.Rol;
import com.udea.bancodigital.usuarios.entity.Usuario;
import com.udea.bancodigital.usuarios.mapper.ClienteMapper;
import com.udea.bancodigital.usuarios.mapper.UsuarioMapper;
import com.udea.bancodigital.usuarios.repository.ClienteRepository;
import com.udea.bancodigital.shared.repository.EstadoRepository;
import com.udea.bancodigital.usuarios.repository.RolRepository;
import com.udea.bancodigital.usuarios.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UsuarioService implements UsuarioApi {

    private static final String ESTADO_INICIAL = "ACTIVO";
    private static final String ROL_REGISTRO = "CLIENTE";

    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final RolRepository rolRepository;
    private final EstadoRepository estadoRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioMapper usuarioMapper;
    private final ClienteMapper clienteMapper;

    @Transactional
    public RegistroUsuarioResponseDTO registrar(RegistroUsuarioRequestDTO request) {

        String email = request.getEmail().trim();

        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new NegocioException(
                    "EMAIL_ALREADY_REGISTERED",
                    MensajesRegistro.CORREO_DUPLICADO,
                    HttpStatus.CONFLICT
            );
        }

        Rol rol = rolRepository.findByNombreIgnoreCase(request.getRol().trim())
                .orElseThrow(() -> new NegocioException(
                        "INVALID_ROLE",
                        "El rol especificado no es válido: " + request.getRol(),
                        HttpStatus.BAD_REQUEST
                ));

        // El registro es publico: solo puede crear clientes. Cualquier otro rol,
        // ADMIN incluido, lo asigna despues un administrador desde la HU9.
        if (!ROL_REGISTRO.equalsIgnoreCase(rol.getNombre())) {
            throw new NegocioException(
                    "ROLE_NOT_ALLOWED",
                    MensajesRegistro.ROL_NO_PERMITIDO,
                    HttpStatus.FORBIDDEN
            );
        }

        Estado estadoActivo = estadoRepository.findByNombreIgnoreCase(ESTADO_INICIAL)
                .orElseThrow(() -> new NegocioException(
                        "INTERNAL_ERROR",
                        "Estado '" + ESTADO_INICIAL + "' no configurado en el sistema",
                        HttpStatus.INTERNAL_SERVER_ERROR
                ));

        OffsetDateTime ahora = OffsetDateTime.now();

        Cliente cliente = clienteMapper.toEntity(request);
        cliente.setEmail(email);
        cliente.setFechaRegistro(ahora);
        Cliente clienteGuardado = clienteRepository.save(cliente);

        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        usuario.setFechaCreacion(ahora);
        usuario.setFechaNacimiento(request.getFechaNacimiento());
        usuario.setClienteId(clienteGuardado.getId());
        usuario.setRol(rol);
        usuario.setEstado(estadoActivo);
        Usuario usuarioGuardado = usuarioRepository.save(usuario);

        return new RegistroUsuarioResponseDTO(
                MensajesRegistro.REGISTRO_EXITOSO,
                usuarioGuardado.getId(),
                usuarioGuardado.getEmail()
        );
    }

    @Transactional(readOnly = true)
    public PerfilUsuarioDTO consultarPerfil(String emailAutenticado) {
        Usuario usuario = buscarPorEmail(emailAutenticado);
        return usuarioMapper.toDTO(usuario, buscarClienteDe(usuario));
    }

    @Transactional
    public PerfilUsuarioDTO actualizarPerfil(
            String emailAutenticado, ActualizarPerfilRequestDTO request) {

        if (request.getDocumentoIdentidad() != null) {
            throw new NegocioException(
                    "FIELD_NOT_EDITABLE",
                    MensajesPerfil.DOCUMENTO_NO_EDITABLE,
                    HttpStatus.BAD_REQUEST
            );
        }

        Usuario usuario = buscarPorEmail(emailAutenticado);
        Cliente cliente = buscarClienteDe(usuario);

        String nuevoEmail = request.getEmail().trim();
        if (usuarioRepository.existsByEmailIgnoreCaseAndIdNot(nuevoEmail, usuario.getId())) {
            throw new NegocioException(
                    "DUPLICATE_EMAIL",
                    MensajesPerfil.CORREO_DE_OTRO_USUARIO,
                    HttpStatus.CONFLICT
            );
        }

        // El correo esta duplicado en las dos tablas desde el registro;
        // si solo se actualizara una, el login y el perfil dejarian de coincidir.
        usuario.setEmail(nuevoEmail);
        cliente.setEmail(nuevoEmail);
        cliente.setTelefono(request.getTelefono().trim());
        cliente.setDireccion(request.getDireccion().trim());

        usuarioRepository.save(usuario);
        clienteRepository.save(cliente);

        return usuarioMapper.toDTO(usuario, cliente);
    }

    @Transactional
    public CambioRolResponseDTO cambiarRol(
            String emailAutenticado, Long usuarioId, CambiarRolRequestDTO request) {

        // Se compara por id y no por correo: el id de la URL es lo que el
        // administrador eligio, y el correo del token es solo su identidad.
        // Sin esta regla un admin podria degradarse y dejar el sistema sin admins.
        Usuario administrador = buscarPorEmail(emailAutenticado);
        if (administrador.getId().equals(usuarioId)) {
            throw new NegocioException(
                    "SELF_ROLE_CHANGE_NOT_ALLOWED",
                    MensajesRol.CAMBIO_PROPIO_ROL,
                    HttpStatus.BAD_REQUEST
            );
        }

        Rol nuevoRol = rolRepository.findByNombreIgnoreCase(request.getRol().trim())
                .orElseThrow(() -> new NegocioException(
                        "INVALID_ROLE",
                        "El rol especificado no es válido: " + request.getRol(),
                        HttpStatus.BAD_REQUEST
                ));

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new NegocioException(
                        "USER_NOT_FOUND", MensajesRol.USUARIO_NO_ENCONTRADO,
                        HttpStatus.NOT_FOUND));

        // JwtAuthenticationFilter lee el rol de la base en cada peticion, asi que
        // el cambio aplica de inmediato aunque el usuario tenga un token vigente.
        usuario.setRol(nuevoRol);
        usuarioRepository.save(usuario);

        return new CambioRolResponseDTO(
                MensajesRol.ROL_ACTUALIZADO,
                usuario.getId(),
                usuario.getEmail(),
                nuevoRol.getNombre()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Long obtenerIdClientePorEmail(String email) {
        return buscarPorEmail(email).getClienteId();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> obtenerRolPorEmail(String email) {
        return usuarioRepository.findByEmail(email)
                .map(usuario -> usuario.getRol().getNombre());
    }

    private Usuario buscarPorEmail(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new NegocioException(
                        "USER_NOT_FOUND", "El usuario autenticado ya no existe",
                        HttpStatus.NOT_FOUND));
    }

    private Cliente buscarClienteDe(Usuario usuario) {
        return clienteRepository.findById(usuario.getClienteId())
                .orElseThrow(() -> new NegocioException(
                        "CLIENT_NOT_FOUND", "El usuario no tiene un cliente asociado",
                        HttpStatus.INTERNAL_SERVER_ERROR));
    }
}

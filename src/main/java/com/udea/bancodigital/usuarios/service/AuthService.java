package com.udea.bancodigital.usuarios.service;

import com.udea.bancodigital.shared.exception.NegocioException;
import com.udea.bancodigital.shared.jwt.JwtService;
import com.udea.bancodigital.shared.jwt.RevocacionTokenService;
import com.udea.bancodigital.usuarios.dto.LoginRequestDTO;
import com.udea.bancodigital.usuarios.dto.LoginResponseDTO;
import com.udea.bancodigital.usuarios.dto.LogoutResponseDTO;
import com.udea.bancodigital.usuarios.entity.Usuario;
import com.udea.bancodigital.usuarios.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;

@Service
public class AuthService {

    private static final int MAX_INTENTOS_FALLIDOS = 5;
    private static final Duration VENTANA_INTENTOS = Duration.ofMinutes(30);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RevocacionTokenService revocacionTokenService;

    public AuthService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RevocacionTokenService revocacionTokenService
    ) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.revocacionTokenService = revocacionTokenService;
    }

    @Transactional(noRollbackFor = {BadCredentialsException.class, NegocioException.class})
    public LoginResponseDTO login(LoginRequestDTO request) {

        // Busca el usuario utilizando el email recibido en la solicitud.
        Usuario usuario = usuarioRepository.findByEmailForUpdate(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Credenciales invalidas"));

        OffsetDateTime ahora = OffsetDateTime.now();
        if (usuario.getBloqueadoHasta() != null && usuario.getBloqueadoHasta().isAfter(ahora)) {
            throw cuentaBloqueada();
        }

        // Compara la contraseña recibida con la contraseña almacenada
        // como hash en la base de datos.
        if (!passwordEncoder.matches(request.getPassword(), usuario.getPasswordHash())) {
            registrarIntentoFallido(usuario, ahora);
            throw new BadCredentialsException("Credenciales invalidas");
        }

        // Verifica que el usuario tenga un estado que permita iniciar sesion.
        if (!usuario.estaActivo()) {
            throw new BadCredentialsException("El usuario no esta activo");
        }

        // Obtiene el nombre del rol asociado al usuario.
        String rol = usuario.getRol().getNombre();

        usuario.setIntentosFallidos(0);
        usuario.setPrimerIntentoFallido(null);
        usuario.setBloqueadoHasta(null);
        usuarioRepository.save(usuario);

        // Genera el JWT con el id (no el correo, que puede cambiar) y el rol del usuario.
        String token = jwtService.generarToken(usuario.getId(), rol);

        // Devuelve el token, el tipo de autenticacion y el rol del usuario.
        return new LoginResponseDTO(token, "Bearer", rol);
    }

    private void registrarIntentoFallido(Usuario usuario, OffsetDateTime ahora) {
        OffsetDateTime primerIntento = usuario.getPrimerIntentoFallido();
        int intentos = usuario.getIntentosFallidos() == null ? 0 : usuario.getIntentosFallidos();

        if (primerIntento == null || !ahora.isBefore(primerIntento.plus(VENTANA_INTENTOS))) {
            usuario.setPrimerIntentoFallido(ahora);
            intentos = 0;
        }

        intentos++;
        usuario.setIntentosFallidos(intentos);

        if (intentos >= MAX_INTENTOS_FALLIDOS) {
            usuario.setBloqueadoHasta(ahora.plus(VENTANA_INTENTOS));
            usuarioRepository.save(usuario);
            throw cuentaBloqueada();
        }

        usuarioRepository.save(usuario);
    }

    private NegocioException cuentaBloqueada() {
        return new NegocioException(
                "ACCOUNT_LOCKED",
                "Cuenta temporalmente bloqueada",
                HttpStatus.FORBIDDEN
        );
    }

    public LogoutResponseDTO logout(String token) {
        // El token ya paso por el filtro JWT (la ruta es protegida), asi que es
        // valido y no estaba revocado: basta con anotarlo en la lista negra.
        revocacionTokenService.revocar(token);
        return new LogoutResponseDTO("Sesion cerrada correctamente");
    }
}

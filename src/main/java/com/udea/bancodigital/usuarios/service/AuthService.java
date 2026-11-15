package com.udea.bancodigital.usuarios.service;

import com.udea.bancodigital.shared.exception.NegocioException;
import com.udea.bancodigital.shared.jwt.JwtService;
import com.udea.bancodigital.shared.jwt.RevocacionTokenService;
import com.udea.bancodigital.usuarios.dto.LoginRequestDTO;
import com.udea.bancodigital.usuarios.dto.LoginResponseDTO;
import com.udea.bancodigital.usuarios.dto.LogoutResponseDTO;
import com.udea.bancodigital.usuarios.entity.Usuario;
import com.udea.bancodigital.usuarios.repository.UsuarioRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final int MAX_INTENTOS_FALLIDOS = 5;
    private static final Duration VENTANA_INTENTOS = Duration.ofMinutes(30);

    // Un unico mensaje para todo login fallido (correo inexistente, contrasena
    // incorrecta o usuario inactivo): la respuesta no debe delatar cual fue el motivo.
    private static final String MENSAJE_CREDENCIALES_INVALIDAS = "Credenciales invalidas";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RevocacionTokenService revocacionTokenService;

    // Hash de una contrasena aleatoria que nadie conoce. Sirve para que un login con un
    // correo inexistente gaste el mismo tiempo de BCrypt que uno con un correo registrado.
    private String hashFicticio;

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

    // Se calcula una sola vez al arrancar, con el mismo encoder (y el mismo costo)
    // que los hashes reales. Asi la primera peticion no paga un calculo extra.
    @PostConstruct
    void prepararHashFicticio() {
        this.hashFicticio = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional(noRollbackFor = {BadCredentialsException.class, NegocioException.class})
    public LoginResponseDTO login(LoginRequestDTO request) {

        // Busca el usuario utilizando el email recibido en la solicitud, normalizado
        // igual que al registrarlo: "Juan@Banco.com" y "juan@banco.com" son el mismo.
        Optional<Usuario> encontrado =
                usuarioRepository.findByEmailForUpdate(Correos.normalizar(request.getEmail()));

        if (encontrado.isEmpty()) {
            igualarTiempoDeRespuesta(request.getPassword());
            throw credencialesInvalidas();
        }

        Usuario usuario = encontrado.get();

        OffsetDateTime ahora = OffsetDateTime.now();
        if (usuario.getBloqueadoHasta() != null && usuario.getBloqueadoHasta().isAfter(ahora)) {
            throw cuentaBloqueada();
        }

        // Compara la contraseña recibida con la contraseña almacenada como hash.
        // La comparacion se hace siempre, y un usuario inactivo falla igual que una
        // contraseña incorrecta: mismo mensaje y mismo conteo de intentos. Si el estado
        // se revelara o no contara como intento, confirmaria que la contraseña era correcta.
        boolean contrasenaCorrecta = passwordEncoder.matches(request.getPassword(), usuario.getPasswordHash());
        if (!contrasenaCorrecta || !usuario.estaActivo()) {
            registrarIntentoFallido(usuario, ahora);
            throw credencialesInvalidas();
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

    // Cuando el correo no existe no hay hash real que comparar, pero se hace una
    // comparacion BCrypt igual de costosa. El resultado no importa: solo el tiempo.
    private void igualarTiempoDeRespuesta(String contrasena) {
        passwordEncoder.matches(contrasena, hashFicticio);
    }

    private BadCredentialsException credencialesInvalidas() {
        return new BadCredentialsException(MENSAJE_CREDENCIALES_INVALIDAS);
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
package com.udea.bancodigital.usuarios.service;

import com.udea.bancodigital.shared.jwt.JwtService;
import com.udea.bancodigital.shared.jwt.RevocacionTokenService;
import com.udea.bancodigital.usuarios.dto.LoginRequestDTO;
import com.udea.bancodigital.usuarios.dto.LoginResponseDTO;
import com.udea.bancodigital.usuarios.dto.LogoutResponseDTO;
import com.udea.bancodigital.usuarios.entity.Usuario;
import com.udea.bancodigital.usuarios.repository.UsuarioRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

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

    public LoginResponseDTO login(LoginRequestDTO request) {

        // Busca el usuario utilizando el email recibido en la solicitud.
        Usuario usuario = usuarioRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Credenciales invalidas"));

        // Compara la contraseña recibida con la contraseña almacenada
        // como hash en la base de datos.
        if (!passwordEncoder.matches(request.getPassword(), usuario.getPasswordHash())) {
            throw new BadCredentialsException("Credenciales invalidas");
        }

        // Verifica que el usuario tenga un estado que permita iniciar sesion.
        if (!usuario.getEstado().getNombre().equalsIgnoreCase("ACTIVO")) {
            throw new BadCredentialsException("El usuario no esta activo");
        }

        // Obtiene el nombre del rol asociado al usuario.
        String rol = usuario.getRol().getNombre();

        // Genera el JWT utilizando el email y el rol del usuario.
        String token = jwtService.generarToken(usuario.getEmail(), rol);

        // Devuelve el token y el tipo de autenticacion esperado por el frontend.
        return new LoginResponseDTO(token, "Bearer");
    }

    public LogoutResponseDTO logout(String token) {
        // El token ya paso por el filtro JWT (la ruta es protegida), asi que es
        // valido y no estaba revocado: basta con anotarlo en la lista negra.
        revocacionTokenService.revocar(token);
        return new LogoutResponseDTO("Sesion cerrada correctamente");
    }
}

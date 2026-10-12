package com.udea.bancodigital.shared.jwt;

import com.udea.bancodigital.usuarios.entity.Usuario;
import com.udea.bancodigital.usuarios.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UsuarioRepository usuarioRepository
    ) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authorizationHeader = request.getHeader("Authorization");

        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorizationHeader.substring(7);

        if (!jwtService.esTokenValido(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        String email = jwtService.extraerEmail(token);

        // Solo se autentica si el usuario existe y sigue activo. Si fue
        // desactivado despues de emitirse el token, no se establece autenticacion.
        usuarioRepository.findByEmail(email)
            .filter(Usuario::estaActivo)
            .ifPresent(usuario -> {
                var autoridad = new SimpleGrantedAuthority("ROLE_" + usuario.getRol().getNombre());

                var autenticacion = new UsernamePasswordAuthenticationToken(
                    usuario.getEmail(),
                    null,
                    List.of(autoridad)
                );

                SecurityContextHolder.getContext().setAuthentication(autenticacion);
            });

        filterChain.doFilter(request, response);
    }
}

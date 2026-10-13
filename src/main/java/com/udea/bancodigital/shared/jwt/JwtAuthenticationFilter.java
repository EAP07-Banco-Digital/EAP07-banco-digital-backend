package com.udea.bancodigital.shared.jwt;

import com.udea.bancodigital.usuarios.api.UsuarioApi;
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

    public static final String PREFIJO_BEARER = "Bearer ";

    // Marca que deja el filtro cuando el token llego revocado, para que
    // CustomAuthEntryPoint responda TOKEN_REVOKED en lugar de UNAUTHORIZED.
    public static final String TOKEN_REVOCADO_ATTR = "tokenRevocado";

    private final JwtService jwtService;
    // Solo la API publica del modulo de usuarios: el filtro vive en shared y no
    // debe conocer la entidad Usuario ni su repositorio.
    private final UsuarioApi usuarioApi;
    private final RevocacionTokenService revocacionTokenService;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UsuarioApi usuarioApi,
            RevocacionTokenService revocacionTokenService
    ) {
        this.jwtService = jwtService;
        this.usuarioApi = usuarioApi;
        this.revocacionTokenService = revocacionTokenService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authorizationHeader = request.getHeader("Authorization");

        if (authorizationHeader == null || !authorizationHeader.startsWith(PREFIJO_BEARER)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorizationHeader.substring(PREFIJO_BEARER.length());

        if (!jwtService.esTokenValido(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Un token revocado sigue con firma y fecha validas: no se autentica, y
        // la peticion sigue para que la cadena de seguridad decida. En una ruta
        // protegida eso termina en 401 TOKEN_REVOKED; en una publica, no estorba.
        if (revocacionTokenService.estaRevocado(token)) {
            request.setAttribute(TOKEN_REVOCADO_ATTR, Boolean.TRUE);
            filterChain.doFilter(request, response);
            return;
        }

        String email = jwtService.extraerEmail(token);

        // El rol se lee de la base en cada peticion, no del claim del token:
        // asi un cambio de rol (HU9) aplica sin esperar a que el token expire.
        usuarioApi.obtenerRolPorEmail(email).ifPresent(rol -> {

            var autenticacion = new UsernamePasswordAuthenticationToken(
                    email,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + rol))
            );

            SecurityContextHolder.getContext().setAuthentication(autenticacion);
        });

        filterChain.doFilter(request, response);
    }
}

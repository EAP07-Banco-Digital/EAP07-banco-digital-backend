package com.udea.bancodigital.shared.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtService {

    // La clave y el tiempo de expiracion vienen de application.properties,
    // que a su vez los lee del .env -- nunca hardcodeados en el codigo.
    private final SecretKey key;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    // El subject es el id del usuario y no su correo: el correo se puede
    // cambiar desde el perfil (HU7) y la sesion abierta no debe caerse por eso.
    public String generarToken(Long usuarioId, String rol) {
        return Jwts.builder()
                // Identificador unico (jti): sin el, dos logins del mismo usuario
                // en el mismo segundo producen el mismo token, y cerrar una
                // sesion revocaria tambien la otra.
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(usuarioId))
                .claim("rol", rol)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    public Long extraerUsuarioId(String token) {
        return Long.valueOf(Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject());
    }

    public Date extraerExpiracion(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration();
    }

    public boolean esTokenValido(String token) {
        try {
            String subject = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            // Los tokens emitidos antes de usar el id como subject traian el
            // correo: se rechazan aqui para que el filtro no falle al leerlos.
            Long.parseLong(subject);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // Token expirado, mal formado, con firma invalida o sin un id como
            // subject (NumberFormatException) -- se trata como invalido, no se
            // propaga la excepcion al llamador.
            return false;
        }
    }
}

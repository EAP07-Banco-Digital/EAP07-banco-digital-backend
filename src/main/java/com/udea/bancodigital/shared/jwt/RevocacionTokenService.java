package com.udea.bancodigital.shared.jwt;

import com.udea.bancodigital.shared.entity.TokenRevocado;
import com.udea.bancodigital.shared.repository.TokenRevocadoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

// Un JWT es valido por si solo hasta que expira: para poder "cerrar sesion"
// antes de eso hay que recordar en el servidor que tokens ya no se aceptan.
@Service
public class RevocacionTokenService {

    private final TokenRevocadoRepository tokenRevocadoRepository;
    private final JwtService jwtService;

    public RevocacionTokenService(TokenRevocadoRepository tokenRevocadoRepository, JwtService jwtService) {
        this.tokenRevocadoRepository = tokenRevocadoRepository;
        this.jwtService = jwtService;
    }

    public void revocar(String token) {
        String hash = hashDe(token);

        // Revocar dos veces el mismo token no es un error: el resultado es el mismo.
        if (tokenRevocadoRepository.existsByTokenHash(hash)) {
            return;
        }

        tokenRevocadoRepository.save(new TokenRevocado(
                null, hash, jwtService.extraerExpiracion(token).toInstant(), Instant.now()
        ));
    }

    public boolean estaRevocado(String token) {
        return tokenRevocadoRepository.existsByTokenHash(hashDe(token));
    }

    // Un token vencido ya lo rechaza la validacion de firma y fecha, asi que
    // su fila en la lista negra no protege nada y se puede borrar.
    @Transactional
    public int purgarExpirados(Instant ahora) {
        return tokenRevocadoRepository.eliminarExpiradosAntesDe(ahora);
    }

    private String hashDe(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // Toda JVM esta obligada a traer SHA-256; si falta, el entorno esta roto.
            throw new IllegalStateException("SHA-256 no esta disponible", e);
        }
    }
}

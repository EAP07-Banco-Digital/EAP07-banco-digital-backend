package com.udea.bancodigital.shared.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Lista negra de tokens JWT cerrados con logout (HU8).
 * Vive en `shared` porque quien la consulta es el filtro JWT, que protege
 * a todos los modulos. Se guarda el hash SHA-256 del token y no el token:
 * si la tabla se filtra, nadie puede reutilizar lo que hay en ella.
 */
@Entity
@Table(name = "tokens_revocados")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TokenRevocado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    // Pasada esta fecha el token ya no valida por si solo, asi que la fila
    // puede purgarse sin riesgo.
    @Column(name = "fecha_expiracion", nullable = false)
    private Instant fechaExpiracion;

    @Column(name = "fecha_revocacion", nullable = false)
    private Instant fechaRevocacion;
}

package com.udea.bancodigital.transferencias.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "transacciones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Se guardan los IDs de las cuentas para evitar acoplamiento JPA
    // con el modulo de cuentas (Monolito Modular)
    @Column(name = "cuenta_origen_id")
    private Long cuentaOrigenId;

    @Column(name = "cuenta_destino_id", nullable = true)
    private Long cuentaDestinoId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tipo_transaccion_id", nullable = false)
    private TipoTransaccion tipoTransaccion;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal monto;

    @Column(name = "fecha_hora", nullable = false)
    private OffsetDateTime fechaHora;

    @Column(length = 200)
    private String descripcion;
}

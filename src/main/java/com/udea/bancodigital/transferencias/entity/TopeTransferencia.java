package com.udea.bancodigital.transferencias.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "topes_transferencia")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopeTransferencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // NULL = topes globales del banco; valor = topes personalizados del cliente
    @Column(name = "cliente_id", nullable = true)
    private Long clienteId;

    @Column(name = "monto_maximo_individual", nullable = false, precision = 15, scale = 2)
    private BigDecimal montoMaximoIndividual;

    @Column(name = "monto_maximo_acumulado", nullable = false, precision = 15, scale = 2)
    private BigDecimal montoMaximoAcumulado;

    // Periodo de aplicacion del tope acumulado (ej. "DIARIO")
    @Column(nullable = false, length = 20)
    private String periodo;

    @Column(name = "fecha_actualizacion", nullable = false)
    private OffsetDateTime fechaActualizacion;
}

package com.udea.bancodigital.usuarios.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
// Una persona se registra una sola vez: la base ya tiene la restriccion
// uq_clientes_documento (tipo_documento, numero_documento). No se declara aqui
// a proposito: con ddl-auto=update, Hibernate la borraria y recrearia en cada
// arranque sobre la base compartida. UsuarioService valida el documento antes.
@Table(name = "clientes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombres;

    @Column(nullable = false)
    private String apellidos;

    @Column(name = "tipo_documento", nullable = false)
    private String tipoDocumento;

    @Column(name = "numero_documento", nullable = false)
    private String numeroDocumento;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String telefono;

    @Column(nullable = false)
    private String direccion;

    @Column(name = "fecha_registro", nullable = false)
    private OffsetDateTime fechaRegistro;

    // @Data genera equals/hashCode sobre todos los campos, lo que rompe con
    // entidades JPA: dos instancias de la misma fila dejan de ser iguales en
    // cuanto una carga perezosa cambia cualquier atributo. Solo cuenta el id.
    @Override
    public boolean equals(Object objeto) {
        if (this == objeto) {
            return true;
        }
        if (!(objeto instanceof Cliente otro)) {
            return false;
        }
        return id != null && id.equals(otro.id);
    }

    // Constante a proposito: el id es null antes de persistir, asi que hashearlo
    // sacaria a la entidad de cualquier HashSet donde ya estuviera.
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Cliente{id=" + id + ", email=" + email + "}";
    }
}

package com.udea.bancodigital.usuarios.entity;

import com.udea.bancodigital.shared.entity.Estado;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "usuarios")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "fecha_creacion", nullable = false)
    private OffsetDateTime fechaCreacion;

    @Column(name = "fecha_nacimiento", nullable = false)
    private LocalDate fechaNacimiento;

    @Column(name = "cliente_id")
    private Long clienteId;

    @ManyToOne
    @JoinColumn(name = "rol_id", nullable = false)
    private Rol rol;

    @ManyToOne
    @JoinColumn(name = "estado_id", nullable = false)
    private Estado estado;

    // @Data genera equals/hashCode sobre todos los campos, lo que rompe con
    // entidades JPA: dos instancias de la misma fila dejan de ser iguales en
    // cuanto una carga perezosa cambia cualquier atributo. Solo cuenta el id.
    @Override
    public boolean equals(Object objeto) {
        if (this == objeto) {
            return true;
        }
        if (!(objeto instanceof Usuario otro)) {
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

    // El usuario es el experto en su propio estado: la regla "esta activo"
    // vive en un solo lugar y la comparten AuthService y el filtro JWT.
    public boolean estaActivo() {
        return estado != null && "ACTIVO".equalsIgnoreCase(estado.getNombre());
    }

    @Override
    public String toString() {
        return "Usuario{id=" + id + ", email=" + email + "}";
    }
}

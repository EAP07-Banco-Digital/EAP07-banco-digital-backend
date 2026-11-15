package com.udea.bancodigital.cuentas.repository;

import com.udea.bancodigital.cuentas.entity.Cuenta;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.List;

public interface CuentaRepository extends JpaRepository<Cuenta, Long> {

    Optional<Cuenta> findByNumeroCuenta(String numeroCuenta);

    boolean existsByNumeroCuenta(String numeroCuenta);

    List<Cuenta> findByClienteIdOrderByFechaAperturaDesc(Long clienteId);

    // HU20: varias cuentas con su estado y tipo en la misma consulta.
    @EntityGraph(attributePaths = {"estado", "tipoCuenta"})
    List<Cuenta> findByIdIn(Collection<Long> ids);

    // SELECT ... FOR UPDATE: la fila queda bloqueada hasta que termine la
    // transaccion, asi dos operaciones sobre la misma cuenta no leen el mismo
    // saldo a la vez y ninguna pisa el resultado de la otra.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cuenta c WHERE c.numeroCuenta = :numeroCuenta")
    Optional<Cuenta> findByNumeroCuentaForUpdate(@Param("numeroCuenta") String numeroCuenta);

    // DEF-HU5-02: serializa las aperturas de un mismo cliente. El candado de
    // PostgreSQL se libera solo al terminar la transaccion, asi que la segunda
    // apertura simultanea espera y luego ve la cuenta que creo la primera.
    // Se envuelve en un SELECT 1 porque pg_advisory_xact_lock devuelve void,
    // un tipo que Hibernate no sabe mapear.
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(:clienteId)) AS candado", nativeQuery = true)
    Integer bloquearAperturasDelCliente(@Param("clienteId") Long clienteId);

    @Query("SELECT COUNT(c) > 0 FROM Cuenta c " +
           "WHERE c.clienteId = :clienteId " +
           "AND UPPER(c.tipoCuenta.nombre) = UPPER(:tipoCuentaNombre) " +
           "AND UPPER(c.estado.nombre) = 'ACTIVA'")
    boolean existsCuentaActivaPorClienteYTipo(
            @Param("clienteId") Long clienteId,
            @Param("tipoCuentaNombre") String tipoCuentaNombre
    );
}

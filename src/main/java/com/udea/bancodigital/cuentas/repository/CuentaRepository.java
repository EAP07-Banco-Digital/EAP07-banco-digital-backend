package com.udea.bancodigital.cuentas.repository;

import com.udea.bancodigital.cuentas.entity.Cuenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface CuentaRepository extends JpaRepository<Cuenta, Long> {

    Optional<Cuenta> findByNumeroCuenta(String numeroCuenta);

    boolean existsByNumeroCuenta(String numeroCuenta);

    @Query("SELECT COUNT(c) > 0 FROM Cuenta c " +
           "WHERE c.clienteId = :clienteId " +
           "AND UPPER(c.tipoCuenta.nombre) = UPPER(:tipoCuentaNombre) " +
           "AND UPPER(c.estado.nombre) = 'ACTIVA'")
    boolean existsCuentaActivaPorClienteYTipo(
            @Param("clienteId") Long clienteId,
            @Param("tipoCuentaNombre") String tipoCuentaNombre
    );
    List<Cuenta> findByClienteIdOrderByFechaAperturaDesc(Long clienteId);
}

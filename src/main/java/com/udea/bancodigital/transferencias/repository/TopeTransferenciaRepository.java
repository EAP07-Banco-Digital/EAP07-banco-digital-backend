package com.udea.bancodigital.transferencias.repository;

import com.udea.bancodigital.transferencias.entity.TopeTransferencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TopeTransferenciaRepository extends JpaRepository<TopeTransferencia, Long> {

    Optional<TopeTransferencia> findByClienteId(Long clienteId);

    Optional<TopeTransferencia> findByClienteIdIsNull();
}

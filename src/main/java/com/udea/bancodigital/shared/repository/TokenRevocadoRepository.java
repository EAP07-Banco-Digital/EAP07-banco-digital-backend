package com.udea.bancodigital.shared.repository;

import com.udea.bancodigital.shared.entity.TokenRevocado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface TokenRevocadoRepository extends JpaRepository<TokenRevocado, Long> {
    boolean existsByTokenHash(String tokenHash);

    // Borrado masivo en una sola sentencia: un deleteBy derivado cargaria
    // cada fila en memoria antes de borrarla una por una.
    @Modifying
    @Query("DELETE FROM TokenRevocado t WHERE t.fechaExpiracion < :limite")
    int eliminarExpiradosAntesDe(@Param("limite") Instant limite);
}

package com.udea.bancodigital.transferencias.repository;

import com.udea.bancodigital.transferencias.entity.Transaccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public interface TransaccionRepository extends JpaRepository<Transaccion, Long>, JpaSpecificationExecutor<Transaccion> {

    // HU16: el tipo viene en la misma consulta (JOIN FETCH) para no hacer una
    // consulta extra por fila al leer su nombre.
    @Query("""
            SELECT t FROM Transaccion t JOIN FETCH t.tipoTransaccion
            WHERE (t.cuentaOrigenId = :cuentaId OR t.cuentaDestinoId = :cuentaId)
              AND t.estado = 'EXITOSA'
              AND t.fechaHora >= :desde AND t.fechaHora < :hasta
            ORDER BY t.fechaHora ASC, t.id ASC
            """)
    List<Transaccion> findMovimientosDeCuenta(
            @Param("cuentaId") Long cuentaId,
            @Param("desde") OffsetDateTime desde,
            @Param("hasta") OffsetDateTime hasta
    );

    // HU16: misma regla que TransaccionApi.TransaccionDetalle#esCreditoPara,
    // sumada en la base para no traer todas las filas posteriores al periodo.
    @Query("""
            SELECT COALESCE(SUM(CASE
                WHEN UPPER(tt.nombre) = 'CREDITO' THEN t.monto
                WHEN UPPER(tt.nombre) = 'DEBITO' THEN -t.monto
                WHEN t.cuentaOrigenId = :cuentaId THEN -t.monto
                ELSE t.monto
            END), 0)
            FROM Transaccion t JOIN t.tipoTransaccion tt
            WHERE (t.cuentaOrigenId = :cuentaId OR t.cuentaDestinoId = :cuentaId)
              AND t.estado = 'EXITOSA'
              AND t.fechaHora >= :desde
            """)
    BigDecimal sumarEfectoNetoDesde(@Param("cuentaId") Long cuentaId, @Param("desde") OffsetDateTime desde);
}

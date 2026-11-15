package com.udea.bancodigital.transferencias.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

/**
 * API pública del módulo de Transferencias / Transacciones.
 * Permite que otros módulos (ej. Cuentas) interactúen con transacciones
 * sin acoplarse directamente a las entidades JPA internas.
 */
public interface TransaccionApi {

    record TransaccionInfo(
            Long transaccionId,
            OffsetDateTime fechaHora
    ) {}

    record TransaccionDetalle(
            Long id,
            Long cuentaOrigenId,
            Long cuentaDestinoId,
            String tipoTransaccion,
            BigDecimal monto,
            OffsetDateTime fechaHora,
            String descripcion
    ) {
        /**
         * Indica si el movimiento aumenta el saldo de la cuenta dada. Un CREDITO
         * siempre suma y un DEBITO siempre resta; una transferencia suma a la
         * cuenta destino y resta a la de origen.
         * <p>
         * {@code TransaccionRepository.sumarEfectoNetoDesde} aplica la misma regla
         * en SQL: si cambia aqui, debe cambiar alla.
         */
        public boolean esCreditoPara(Long cuentaId) {
            if (TIPO_CREDITO.equalsIgnoreCase(tipoTransaccion)) {
                return true;
            }
            if (TIPO_DEBITO.equalsIgnoreCase(tipoTransaccion)) {
                return false;
            }
            return !cuentaId.equals(cuentaOrigenId);
        }
    }

    String TIPO_CREDITO = "CREDITO";
    String TIPO_DEBITO = "DEBITO";

    TransaccionInfo registrarTransaccion(
            Long cuentaOrigenId,
            Long cuentaDestinoId,
            String tipoTransaccionNombre,
            BigDecimal monto,
            String descripcion
    );

    Page<TransaccionDetalle> consultarHistorial(
            Long cuentaId,
            OffsetDateTime desde,
            OffsetDateTime hasta,
            String tipoTransaccion,
            Pageable pageable
    );

    /**
     * HU16: movimientos exitosos de una cuenta (como origen o destino) con
     * {@code desde <= fechaHora < hasta}, en orden cronologico.
     */
    List<TransaccionDetalle> listarMovimientos(Long cuentaId, OffsetDateTime desde, OffsetDateTime hasta);

    /**
     * HU16: efecto neto sobre el saldo de la cuenta (creditos menos debitos) de
     * los movimientos exitosos con {@code fechaHora >= desde}. Sirve para
     * reconstruir el saldo que tenia la cuenta en una fecha pasada a partir de
     * su saldo actual. Devuelve cero si no hay movimientos.
     */
    BigDecimal calcularEfectoNetoDesde(Long cuentaId, OffsetDateTime desde);

    /**
     * HU20: una operacion tal como la ve un administrador, con su estado.
     */
    record OperacionInfo(
            Long id,
            Long cuentaOrigenId,
            Long cuentaDestinoId,
            String tipoTransaccion,
            BigDecimal monto,
            String estado,
            OffsetDateTime fechaHora,
            String descripcion
    ) {}

    /**
     * HU20: operaciones que cumplen todos los filtros dados, en orden
     * cronologico. Un filtro nulo no restringe.
     *
     * @param desde     fecha y hora minima (incluida)
     * @param hasta     fecha y hora maxima (excluida)
     * @param cuentaIds operaciones donde alguna de estas cuentas es origen o destino;
     *                  una coleccion vacia no devuelve nada
     * @param estado    estado de la transaccion (ej. EXITOSA), sin distinguir mayusculas
     */
    List<OperacionInfo> buscarOperaciones(
            OffsetDateTime desde,
            OffsetDateTime hasta,
            Collection<Long> cuentaIds,
            String estado
    );
}

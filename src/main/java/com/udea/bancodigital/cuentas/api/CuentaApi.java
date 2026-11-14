package com.udea.bancodigital.cuentas.api;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * API pública del módulo de Cuentas.
 * <p>
 * Define las operaciones que otros módulos (ej. transferencias) pueden
 * realizar sobre las cuentas. Utiliza tipos de datos estándar o records/DTOs
 * para no exponer las entidades JPA.
 */
public interface CuentaApi {

    /**
     * DTO de información básica de la cuenta devuelta a otros módulos.
     */
    record CuentaInfo(
            Long id,
            Long clienteId,
            String numeroCuenta,
            BigDecimal saldoDisponible,
            String estadoNombre
    ) {}

    /**
     * Busca la información de una cuenta por su número.
     * <p>
     * Devuelve vacío si no existe, en lugar de lanzar un error: solo quien llama
     * sabe qué papel cumple la cuenta (origen, destino, consultada) y, por lo
     * tanto, qué error corresponde (DEF-HU12-07).
     *
     * @param numeroCuenta el número de cuenta
     * @return la información de la cuenta, o vacío si no existe
     */
    Optional<CuentaInfo> obtenerInfoPorNumeroCuenta(String numeroCuenta);

    /**
     * Igual que {@link #obtenerInfoPorNumeroCuenta(String)}, pero bloquea la fila
     * de la cuenta hasta que termine la transacción del llamador.
     * <p>
     * Es la lectura que se debe usar antes de {@link #actualizarSaldo(Long, BigDecimal)}:
     * sin el bloqueo, dos operaciones simultáneas leen el mismo saldo y una de las
     * dos actualizaciones se pierde (doble gasto). Solo puede llamarse dentro de una
     * transacción; si se bloquean varias cuentas, debe hacerse siempre en el mismo
     * orden para no provocar un interbloqueo.
     *
     * @param numeroCuenta el número de cuenta
     * @return la información con el saldo vigente, o vacío si no existe
     */
    Optional<CuentaInfo> bloquearPorNumeroCuenta(String numeroCuenta);

    /**
     * Actualiza el saldo de una cuenta existente.
     * @param cuentaId ID interno de la cuenta
     * @param nuevoSaldo nuevo saldo a establecer
     */
    void actualizarSaldo(Long cuentaId, BigDecimal nuevoSaldo);
}

package com.udea.bancodigital.cuentas.api;

import java.math.BigDecimal;

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
     * @param numeroCuenta el número de cuenta
     * @return CuentaInfo si existe, de lo contrario lanza excepción de negocio
     */
    CuentaInfo obtenerInfoPorNumeroCuenta(String numeroCuenta);

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
     * @return CuentaInfo con el saldo vigente, de lo contrario lanza excepción de negocio
     */
    CuentaInfo bloquearPorNumeroCuenta(String numeroCuenta);

    /**
     * Actualiza el saldo de una cuenta existente.
     * @param cuentaId ID interno de la cuenta
     * @param nuevoSaldo nuevo saldo a establecer
     */
    void actualizarSaldo(Long cuentaId, BigDecimal nuevoSaldo);
}

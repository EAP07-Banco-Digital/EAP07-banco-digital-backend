package com.udea.bancodigital.cuentas.api;

import com.udea.bancodigital.cuentas.entity.Cuenta;
import com.udea.bancodigital.cuentas.repository.CuentaRepository;
import com.udea.bancodigital.shared.exception.NegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Implementación de la API pública del módulo de Cuentas.
 * <p>
 * Esta clase expone únicamente las operaciones necesarias para la comunicación
 * inter-módulos (ej. desde Transferencias), sin dependencias circulares.
 * Solo inyecta repositorios del módulo de Cuentas.
 */
@Service
@RequiredArgsConstructor
public class CuentaApiImpl implements CuentaApi {

    private final CuentaRepository cuentaRepository;

    @Override
    @Transactional(readOnly = true)
    public CuentaInfo obtenerInfoPorNumeroCuenta(String numeroCuenta) {
        Cuenta cuenta = cuentaRepository.findByNumeroCuenta(numeroCuenta)
                .orElseThrow(() -> new NegocioException(
                        "DESTINATION_ACCOUNT_NOT_FOUND",
                        "La cuenta " + numeroCuenta + " no existe",
                        HttpStatus.NOT_FOUND
                ));

        return new CuentaInfo(
                cuenta.getId(),
                cuenta.getClienteId(),
                cuenta.getNumeroCuenta(),
                cuenta.getSaldoDisponible(),
                cuenta.getEstado().getNombre()
        );
    }

    @Override
    @Transactional
    public void actualizarSaldo(Long cuentaId, BigDecimal nuevoSaldo) {
        Cuenta cuenta = cuentaRepository.findById(cuentaId)
                .orElseThrow(() -> new NegocioException(
                        "INTERNAL_ERROR",
                        "Cuenta " + cuentaId + " no encontrada al actualizar saldo",
                        HttpStatus.INTERNAL_SERVER_ERROR
                ));
        cuenta.setSaldoDisponible(nuevoSaldo);
        cuentaRepository.save(cuenta);
    }
}

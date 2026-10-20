package com.udea.bancodigital.cuentas.service;

import com.udea.bancodigital.cuentas.dto.AperturaCuentaRequest;
import com.udea.bancodigital.cuentas.dto.CuentaResponse;
import com.udea.bancodigital.cuentas.dto.CuentaResumenResponse;
import com.udea.bancodigital.cuentas.dto.HistorialTransaccionResponse;
import com.udea.bancodigital.cuentas.dto.OperacionCuentaRequest;
import com.udea.bancodigital.cuentas.dto.OperacionCuentaResponse;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;

public interface CuentaService {
    CuentaResponse solicitarAperturaCuenta(Long clienteId, AperturaCuentaRequest request);
    CuentaResponse consultarSaldo(Long clienteId, String numeroCuenta);
    OperacionCuentaResponse registrarCredito(Long clienteId, String numeroCuenta, OperacionCuentaRequest request);
    OperacionCuentaResponse registrarDebito(Long clienteId, String numeroCuenta, OperacionCuentaRequest request);
    Page<HistorialTransaccionResponse> consultarHistorial(
            Long clienteId,
            String numeroCuenta,
            LocalDate desde,
            LocalDate hasta,
            String tipoTransaccion,
            int page,
            int size
    );

    /**
     * HU11: lista las cuentas del cliente autenticado.
     * @param clienteId id del cliente autenticado (sale del JWT)
     * @param clienteIdSolicitado id de cliente pedido en la consulta (opcional);
     *                            si es distinto al autenticado se responde 403
     */
    List<CuentaResumenResponse> listarCuentas(Long clienteId, Long clienteIdSolicitado);
}
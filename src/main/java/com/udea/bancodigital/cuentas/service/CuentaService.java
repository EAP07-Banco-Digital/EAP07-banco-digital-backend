package com.udea.bancodigital.cuentas.service;

import com.udea.bancodigital.cuentas.dto.AperturaCuentaRequest;
import com.udea.bancodigital.cuentas.dto.CuentaResponse;
import com.udea.bancodigital.cuentas.dto.CuentaResumenResponse;

import java.util.List;

public interface CuentaService {
    CuentaResponse solicitarAperturaCuenta(Long clienteId, AperturaCuentaRequest request);
    CuentaResponse consultarSaldo(Long clienteId, String numeroCuenta);

    /**
     * HU11: lista las cuentas del cliente autenticado.
     * @param clienteId id del cliente autenticado (sale del JWT)
     * @param clienteIdSolicitado id de cliente pedido en la consulta (opcional);
     *                            si es distinto al autenticado se responde 403
     */
    List<CuentaResumenResponse> listarCuentas(Long clienteId, Long clienteIdSolicitado);
}
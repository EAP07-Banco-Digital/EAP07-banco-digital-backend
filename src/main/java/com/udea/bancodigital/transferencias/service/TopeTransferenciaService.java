package com.udea.bancodigital.transferencias.service;

import com.udea.bancodigital.transferencias.dto.TopeTransferenciaResponse;

public interface TopeTransferenciaService {

    /**
     * Consulta los topes de transferencia aplicables a un cliente.
     * Si el cliente cuenta con topes personalizados, se retornan dichos topes.
     * En caso contrario, se retornan los topes predeterminados (globales) del banco.
     *
     * @param clienteId ID del cliente autenticado
     * @return DTO con los límites por transferencia, acumulado, periodo y si es personalizado
     */
    TopeTransferenciaResponse consultarTopesParaCliente(Long clienteId);
}

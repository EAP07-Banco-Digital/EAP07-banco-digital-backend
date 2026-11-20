package com.udea.bancodigital.transferencias.service;

import com.udea.bancodigital.transferencias.dto.ConfigurarTopesRequest;
import com.udea.bancodigital.transferencias.dto.TopeTransferenciaResponse;

public interface TopeTransferenciaService {

    /**
     * Consulta los topes de transferencia aplicables a un cliente (HU17).
     * Si el cliente cuenta con topes personalizados, se retornan dichos topes.
     * En caso contrario, se retornan los topes predeterminados (globales) del
     * 
     * banco.
     *
     * @param clienteId ID del cliente autenticado
     *         
     * @return DTO con los límites por transferencia, acumulado, periodo y si es
     *         personalizado
     */
    TopeTransferenciaResponse consultarTopesParaCliente(Long clienteId);

    /**
     * Configura y persiste los topes globales de transferencia del banco (HU18).
     * Solo debe ser invocado por administradores autorizados.
     *                
     *
     * @param request Datos con el monto máximo individual y el acumulado del
     *                periodo
     * @return Configuración actualizada persistida
     */
    TopeTransferenciaResponse configurarTopesGlobales(ConfigurarTopesRequest request);

    /**
     * Consulta la configuración global actual de los topes del banco (HU18).
     *
     * @return Configuración global actual
     */
    TopeTransferenciaResponse consultarTopesGlobales();
}

package com.udea.bancodigital.transferencias.service;

import com.udea.bancodigital.shared.exception.CodigosError;
import com.udea.bancodigital.shared.exception.NegocioException;
import com.udea.bancodigital.transferencias.dto.TopeTransferenciaResponse;
import com.udea.bancodigital.transferencias.entity.TopeTransferencia;
import com.udea.bancodigital.transferencias.repository.TopeTransferenciaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TopeTransferenciaServiceImpl implements TopeTransferenciaService {

    private final TopeTransferenciaRepository topeTransferenciaRepository;

    @Override
    @Transactional(readOnly = true)
    public TopeTransferenciaResponse consultarTopesParaCliente(Long clienteId) {
        if (clienteId == null) {
            throw new NegocioException(CodigosError.UNAUTHORIZED, "Usuario no autenticado", HttpStatus.UNAUTHORIZED);
        }

        // 1. Escenario 1: Consultar si el cliente tiene topes personalizados
        Optional<TopeTransferencia> topePersonalizado = topeTransferenciaRepository.findByClienteId(clienteId);
        if (topePersonalizado.isPresent()) {
            TopeTransferencia tope = topePersonalizado.get();
            return TopeTransferenciaResponse.builder()
                    .montoMaximoPorTransferencia(tope.getMontoMaximoIndividual())
                    .montoMaximoAcumuladoPeriodo(tope.getMontoMaximoAcumulado())
                    .periodo(tope.getPeriodo())
                    .esPersonalizado(true)
                    .build();
        }

        // 2. Escenario 2: Consultar los topes predeterminados del banco (cliente_id IS NULL)
        Optional<TopeTransferencia> topeGlobal = topeTransferenciaRepository.findByClienteIdIsNull();
        if (topeGlobal.isPresent()) {
            TopeTransferencia tope = topeGlobal.get();
            return TopeTransferenciaResponse.builder()
                    .montoMaximoPorTransferencia(tope.getMontoMaximoIndividual())
                    .montoMaximoAcumuladoPeriodo(tope.getMontoMaximoAcumulado())
                    .periodo(tope.getPeriodo())
                    .esPersonalizado(false)
                    .build();
        }

        // 3. Si no existen ni personalizados ni predeterminados, se notifica que no están configurados
        throw new NegocioException(
                "TOPES_NO_CONFIGURADOS",
                "Los topes de transferencia aún no han sido configurados",
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }
}

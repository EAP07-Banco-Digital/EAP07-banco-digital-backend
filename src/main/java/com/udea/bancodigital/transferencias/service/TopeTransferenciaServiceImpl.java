package com.udea.bancodigital.transferencias.service;

import com.udea.bancodigital.shared.exception.CodigosError;
import com.udea.bancodigital.shared.exception.NegocioException;
import com.udea.bancodigital.transferencias.dto.ConfigurarTopesRequest;
import com.udea.bancodigital.transferencias.dto.TopeTransferenciaResponse;
import com.udea.bancodigital.transferencias.entity.TopeTransferencia;
import com.udea.bancodigital.transferencias.repository.TopeTransferenciaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TopeTransferenciaServiceImpl implements TopeTransferenciaService {

    private static final String PERIODO_PREDETERMINADO = "DIARIO";

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
            return mapearARespuesta(tope, true);
        }

        // 2. Escenario 2: Consultar los topes predeterminados del banco (cliente_id IS
        // 
        // NULL)
        Optional<TopeTransferencia> topeGlobal = topeTransferenciaRepository.findByClienteIdIsNull();
        if (topeGlobal.isPresent()) {
            TopeTransferencia tope = topeGlobal.get();
            return mapearARespuesta(tope, false);
        }

        // 
        // 3. Si no existen ni personalizados ni predeterminados, se notifica que no
        // están configurados
        throw new NegocioException(
                "TOPES_NO_CONFIGURADOS",        "Los topes de transferencia aún no han sido configurados",
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Override
    @Transactional
    public TopeTransferenciaResponse configurarTopesGlobales(ConfigurarTopesRequest request) {
        if (request == null) {
                    
            throw new NegocioException("VALIDATION_ERROR", "La solicitud de configuración es requerida",
                    HttpStatus.BAD_REQUEST);
        }

        BigDecimal individual = request.getMontoMaximoPorTransferencia();
        BigDecimal acumulado = request.getMontoMaximoAcumuladoPeriodo();

                    
        if (individual == null || individual.compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException("INVALID_LIMITS", "El monto máximo por transferencia debe ser mayor a 0",
                    HttpStatus.BAD_REQUEST);
        }
                    

        if (acumulado == null || acumulado.compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException("INVALID_LIMITS", "El límite acumulado del periodo debe ser mayor a 0",
                    HttpStatus.BAD_REQUEST);
                    
                    
        }

        if (acumulado.compareTo(individual) < 0) {
        // 
            throw new NegocioException("INVALID_LIMITS",
                    "El límite acumulado no puede ser inferior al monto máximo por transferencia",
                    HttpStatus.BAD_REQUEST);
        }

        // Buscar registro global existente para actualizarlo, o crear uno nuevo si aún
        // no existe
        TopeTransferencia global = topeTransferenciaRepository.findByClienteIdIsNull()
                .orElseGet(() -> TopeTransferencia.builder()
                        .clienteId(null)
                        .periodo(PERIODO_PREDETERMINADO)
                        .build());

        global.setMontoMaximoIndividual(individual);
        global.setMontoMaximoAcumulado(acumulado);
        if (global.getPeriodo() == null) {
            global.setPeriodo(PERIODO_PREDETERMINADO);
        }
        global.setFechaActualizacion(OffsetDateTime.now());

        TopeTransferencia guardado = topeTransferenciaRepository.save(global);
        return mapearARespuesta(guardado, false);
    }

    @Override
    @Transactional(readOnly = true)
    public TopeTransferenciaResponse consultarTopesGlobales() {
        TopeTransferencia global = topeTransferenciaRepository.findByClienteIdIsNull()
                .orElseThrow(() -> new NegocioException(
                        "TOPES_NO_CONFIGURADOS",
                        "Los topes de transferencia aún no han sido configurados",
                        HttpStatus.SERVICE_UNAVAILABLE));

        return mapearARespuesta(global, false);
    }

    private TopeTransferenciaResponse mapearARespuesta(TopeTransferencia tope, boolean esPersonalizado) {
        return TopeTransferenciaResponse.builder()
                .montoMaximoPorTransferencia(tope.getMontoMaximoIndividual())
                .montoMaximoAcumuladoPeriodo(tope.getMontoMaximoAcumulado())
                .periodo(tope.getPeriodo())
                .esPersonalizado(esPersonalizado)
                .build();
    }
}

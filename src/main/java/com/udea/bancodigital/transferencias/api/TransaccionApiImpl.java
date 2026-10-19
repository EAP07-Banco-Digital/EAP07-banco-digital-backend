package com.udea.bancodigital.transferencias.api;

import com.udea.bancodigital.shared.exception.NegocioException;
import com.udea.bancodigital.transferencias.entity.TipoTransaccion;
import com.udea.bancodigital.transferencias.entity.Transaccion;
import com.udea.bancodigital.transferencias.repository.TipoTransaccionRepository;
import com.udea.bancodigital.transferencias.repository.TransaccionRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementación de la API pública del módulo de Transferencias/Transacciones.
 * <p>
 * Esta clase expone únicamente las operaciones necesarias para la comunicación
 * inter-módulos (ej. desde Cuentas), sin dependencias circulares.
 * Solo inyecta repositorios del módulo de Transferencias.
 */
@Service
@RequiredArgsConstructor
public class TransaccionApiImpl implements TransaccionApi {

    private final TransaccionRepository transaccionRepository;
    private final TipoTransaccionRepository tipoTransaccionRepository;

    @Override
    @Transactional
    public TransaccionInfo registrarTransaccion(
            Long cuentaOrigenId,
            Long cuentaDestinoId,
            String tipoTransaccionNombre,
            BigDecimal monto,
            String descripcion
    ) {
        TipoTransaccion tipo = tipoTransaccionRepository.findByNombreIgnoreCase(tipoTransaccionNombre)
                .orElseThrow(() -> new NegocioException(
                        "INTERNAL_ERROR",
                        "Tipo de transacción '" + tipoTransaccionNombre + "' no configurado",
                        HttpStatus.INTERNAL_SERVER_ERROR
                ));

        Transaccion transaccion = Transaccion.builder()
                .cuentaOrigenId(cuentaOrigenId)
                .cuentaDestinoId(cuentaDestinoId)
                .tipoTransaccion(tipo)
                .monto(monto)
                .estado("EXITOSA")
                .descripcion(descripcion)
                .fechaHora(OffsetDateTime.now())
                .build();

        Transaccion guardada = transaccionRepository.save(transaccion);
        return new TransaccionInfo(guardada.getId(), guardada.getFechaHora());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransaccionDetalle> consultarHistorial(
            Long cuentaId,
            OffsetDateTime desde,
            OffsetDateTime hasta,
            String tipoTransaccion,
            Pageable pageable
    ) {
        Long tipoId = null;
        if (tipoTransaccion != null && !tipoTransaccion.isBlank()) {
            TipoTransaccion tipo = tipoTransaccionRepository.findByNombreIgnoreCase(tipoTransaccion.trim())
                    .orElseThrow(() -> new NegocioException(
                            "INVALID_TRANSACTION_TYPE",
                            "Tipo de transacción no válido: " + tipoTransaccion,
                            HttpStatus.BAD_REQUEST
                    ));
            tipoId = tipo.getId();
        }

        final Long finalTipoId = tipoId;
        Specification<Transaccion> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.or(
                    cb.equal(root.get("cuentaOrigenId"), cuentaId),
                    cb.equal(root.get("cuentaDestinoId"), cuentaId)
            ));

            if (desde != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("fechaHora"), desde));
            }

            if (hasta != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("fechaHora"), hasta));
            }

            if (finalTipoId != null) {
                predicates.add(cb.equal(root.get("tipoTransaccion").get("id"), finalTipoId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Transaccion> transaccionesPage = transaccionRepository.findAll(spec, pageable);

        return transaccionesPage.map(t -> new TransaccionDetalle(
                t.getId(),
                t.getCuentaOrigenId(),
                t.getCuentaDestinoId(),
                t.getTipoTransaccion().getNombre(),
                t.getMonto(),
                t.getFechaHora(),
                t.getDescripcion()
        ));
    }
}

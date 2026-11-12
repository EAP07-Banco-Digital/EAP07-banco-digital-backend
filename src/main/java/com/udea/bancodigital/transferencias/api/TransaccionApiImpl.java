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
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
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

        return transaccionesPage.map(TransaccionApiImpl::aDetalle);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransaccionDetalle> listarMovimientos(Long cuentaId, OffsetDateTime desde, OffsetDateTime hasta) {
        return transaccionRepository.findMovimientosDeCuenta(cuentaId, desde, hasta).stream()
                .map(TransaccionApiImpl::aDetalle)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal calcularEfectoNetoDesde(Long cuentaId, OffsetDateTime desde) {
        BigDecimal neto = transaccionRepository.sumarEfectoNetoDesde(cuentaId, desde);
        return neto != null ? neto : BigDecimal.ZERO;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OperacionInfo> buscarOperaciones(
            OffsetDateTime desde,
            OffsetDateTime hasta,
            Collection<Long> cuentaIds,
            String estado
    ) {
        if (cuentaIds != null && cuentaIds.isEmpty()) {
            return List.of();
        }
        Specification<Transaccion> spec = (root, query, cb) -> {
            // El tipo viene en la misma consulta: sin el fetch habria una
            // consulta extra para leer el nombre del tipo.
            if (Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("tipoTransaccion");
            }
            List<Predicate> predicados = new ArrayList<>();
            if (desde != null) {
                predicados.add(cb.greaterThanOrEqualTo(root.get("fechaHora"), desde));
            }
            if (hasta != null) {
                predicados.add(cb.lessThan(root.get("fechaHora"), hasta));
            }
            if (cuentaIds != null) {
                predicados.add(cb.or(
                        root.get("cuentaOrigenId").in(cuentaIds),
                        root.get("cuentaDestinoId").in(cuentaIds)));
            }
            if (estado != null && !estado.isBlank()) {
                predicados.add(cb.equal(cb.upper(root.get("estado")), estado.trim().toUpperCase(Locale.ROOT)));
            }
            return cb.and(predicados.toArray(new Predicate[0]));
        };

        return transaccionRepository.findAll(spec, Sort.by("fechaHora", "id")).stream()
                .map(t -> new OperacionInfo(
                        t.getId(),
                        t.getCuentaOrigenId(),
                        t.getCuentaDestinoId(),
                        t.getTipoTransaccion().getNombre(),
                        t.getMonto(),
                        t.getEstado(),
                        t.getFechaHora(),
                        t.getDescripcion()))
                .toList();
    }

    private static TransaccionDetalle aDetalle(Transaccion t) {
        return new TransaccionDetalle(
                t.getId(),
                t.getCuentaOrigenId(),
                t.getCuentaDestinoId(),
                t.getTipoTransaccion().getNombre(),
                t.getMonto(),
                t.getFechaHora(),
                t.getDescripcion()
        );
    }
}

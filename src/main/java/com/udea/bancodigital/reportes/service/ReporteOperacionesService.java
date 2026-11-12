package com.udea.bancodigital.reportes.service;

import com.udea.bancodigital.cuentas.api.CuentaApi;
import com.udea.bancodigital.cuentas.api.CuentaApi.CuentaInfo;
import com.udea.bancodigital.reportes.dto.FiltroOperaciones;
import com.udea.bancodigital.reportes.dto.ReporteOperacionesResponse;
import com.udea.bancodigital.reportes.dto.ReporteOperacionesResponse.Operacion;
import com.udea.bancodigital.reportes.dto.ReporteOperacionesResponse.Participante;
import com.udea.bancodigital.reportes.dto.ReporteOperacionesResponse.Resumen;
import com.udea.bancodigital.reportes.dto.ReporteOperacionesResponse.TotalPorTipo;
import com.udea.bancodigital.shared.exception.NegocioException;
import com.udea.bancodigital.transferencias.api.TransaccionApi;
import com.udea.bancodigital.transferencias.api.TransaccionApi.OperacionInfo;
import com.udea.bancodigital.usuarios.api.UsuarioApi;
import com.udea.bancodigital.usuarios.api.UsuarioApi.ClienteResumen;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * HU20: reportes administrativos de operaciones.
 * <p>
 * Es un modulo de lectura que cruza los otros tres solo a traves de sus
 * contratos {@code api/}. Cada fuente se consulta una vez por reporte (y no
 * una vez por operacion): operaciones, luego todas sus cuentas juntas, luego
 * todos sus clientes juntos.
 */
@Service
@RequiredArgsConstructor
public class ReporteOperacionesService {

    private final TransaccionApi transaccionApi;
    private final CuentaApi cuentaApi;
    private final UsuarioApi usuarioApi;

    public ReporteOperacionesResponse generar(FiltroOperaciones filtro) {
        FiltroOperaciones normalizado = normalizar(filtro);
        validarRango(normalizado);

        List<OperacionInfo> operaciones = buscarOperaciones(normalizado);

        Map<Long, CuentaInfo> cuentas = cuentaApi.obtenerInfoPorIds(idsDeCuentas(operaciones));
        Map<Long, ClienteResumen> clientes = cuentas.isEmpty()
                ? Map.of()
                : usuarioApi.obtenerClientesPorIds(idsDeClientes(cuentas.values()));

        List<Operacion> detalle = operaciones.stream()
                .map(op -> new Operacion(
                        op.id(),
                        op.fechaHora(),
                        op.tipoTransaccion(),
                        op.estado(),
                        dinero(op.monto()),
                        op.descripcion(),
                        participante(op.cuentaOrigenId(), cuentas, clientes),
                        participante(op.cuentaDestinoId(), cuentas, clientes)))
                .toList();

        return new ReporteOperacionesResponse(
                OffsetDateTime.now(), normalizado, resumir(operaciones), detalle);
    }

    private static FiltroOperaciones normalizar(FiltroOperaciones filtro) {
        return new FiltroOperaciones(
                filtro.fechaInicio(),
                filtro.fechaFin(),
                filtro.clienteId(),
                textoONulo(filtro.numeroCuenta()),
                filtro.estado() == null || filtro.estado().isBlank() ? null : filtro.estado().trim().toUpperCase());
    }

    private static String textoONulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private static void validarRango(FiltroOperaciones filtro) {
        if (filtro.fechaInicio() != null && filtro.fechaFin() != null
                && filtro.fechaInicio().isAfter(filtro.fechaFin())) {
            throw new NegocioException(
                    "RANGO_FECHAS_INVALIDO",
                    "La fecha de inicio no puede ser posterior a la fecha de fin",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private List<OperacionInfo> buscarOperaciones(FiltroOperaciones filtro) {
        Set<Long> cuentaIds = null;
        if (filtro.clienteId() != null || filtro.numeroCuenta() != null) {
            cuentaIds = new HashSet<>();
            for (CuentaInfo cuenta : cuentaApi.buscarCuentas(filtro.clienteId(), filtro.numeroCuenta())) {
                cuentaIds.add(cuenta.id());
            }
            // Ningun cliente/cuenta coincide: el reporte sale vacio, sin consultar operaciones.
            if (cuentaIds.isEmpty()) {
                return List.of();
            }
        }
        // Mismo criterio de dias que los reportes de cuenta: UTC, fin exclusivo.
        OffsetDateTime desde = filtro.fechaInicio() == null ? null
                : filtro.fechaInicio().atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime hasta = filtro.fechaFin() == null ? null
                : filtro.fechaFin().plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        return transaccionApi.buscarOperaciones(desde, hasta, cuentaIds, filtro.estado());
    }

    private static Set<Long> idsDeCuentas(List<OperacionInfo> operaciones) {
        Set<Long> ids = new HashSet<>();
        for (OperacionInfo op : operaciones) {
            ids.add(op.cuentaOrigenId());
            ids.add(op.cuentaDestinoId());
        }
        ids.remove(null);
        return ids;
    }

    private static Set<Long> idsDeClientes(Collection<CuentaInfo> cuentas) {
        Set<Long> ids = new HashSet<>();
        cuentas.forEach(cuenta -> ids.add(cuenta.clienteId()));
        ids.remove(null);
        return ids;
    }

    private static Participante participante(Long cuentaId, Map<Long, CuentaInfo> cuentas,
                                             Map<Long, ClienteResumen> clientes) {
        if (cuentaId == null) {
            return null;
        }
        CuentaInfo cuenta = cuentas.get(cuentaId);
        if (cuenta == null) {
            return new Participante(null, null, null, null, null);
        }
        ClienteResumen cliente = clientes.get(cuenta.clienteId());
        return new Participante(
                cuenta.numeroCuenta(),
                cuenta.clienteId(),
                cliente == null ? null : cliente.nombreCompleto(),
                cliente == null ? null : cliente.tipoDocumento(),
                cliente == null ? null : cliente.numeroDocumento());
    }

    private static Resumen resumir(List<OperacionInfo> operaciones) {
        BigDecimal montoTotal = BigDecimal.ZERO;
        // TreeMap: los tipos salen siempre en el mismo orden (alfabetico).
        Map<String, long[]> cantidades = new TreeMap<>();
        Map<String, BigDecimal> montos = new TreeMap<>();
        for (OperacionInfo op : operaciones) {
            montoTotal = montoTotal.add(op.monto());
            cantidades.computeIfAbsent(op.tipoTransaccion(), tipo -> new long[1])[0]++;
            montos.merge(op.tipoTransaccion(), op.monto(), BigDecimal::add);
        }
        List<TotalPorTipo> porTipo = cantidades.entrySet().stream()
                .map(e -> new TotalPorTipo(e.getKey(), e.getValue()[0], dinero(montos.get(e.getKey()))))
                .toList();
        return new Resumen(operaciones.size(), dinero(montoTotal), porTipo);
    }

    // Todo valor monetario sale con dos decimales, como en la base.
    private static BigDecimal dinero(BigDecimal valor) {
        return Objects.requireNonNull(valor).setScale(2, RoundingMode.HALF_EVEN);
    }
}

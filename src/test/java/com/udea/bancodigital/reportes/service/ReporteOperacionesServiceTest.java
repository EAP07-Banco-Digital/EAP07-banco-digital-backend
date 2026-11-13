package com.udea.bancodigital.reportes.service;

import com.udea.bancodigital.cuentas.api.CuentaApi;
import com.udea.bancodigital.cuentas.api.CuentaApi.CuentaInfo;
import com.udea.bancodigital.reportes.dto.FiltroOperaciones;
import com.udea.bancodigital.reportes.dto.ReporteOperacionesResponse;
import com.udea.bancodigital.reportes.dto.ReporteOperacionesResponse.Operacion;
import com.udea.bancodigital.shared.exception.NegocioException;
import com.udea.bancodigital.transferencias.api.TransaccionApi;
import com.udea.bancodigital.transferencias.api.TransaccionApi.OperacionInfo;
import com.udea.bancodigital.usuarios.api.UsuarioApi;
import com.udea.bancodigital.usuarios.api.UsuarioApi.ClienteResumen;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * HU20: reporte administrativo de operaciones.
 */
@ExtendWith(MockitoExtension.class)
class ReporteOperacionesServiceTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 10, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 10, 31);
    private static final OffsetDateTime DESDE = OffsetDateTime.parse("2026-10-01T00:00:00-05:00");
    private static final OffsetDateTime HASTA = OffsetDateTime.parse("2026-11-01T00:00:00-05:00");

    @Mock
    private TransaccionApi transaccionApi;

    @Mock
    private CuentaApi cuentaApi;

    @Mock
    private UsuarioApi usuarioApi;

    @InjectMocks
    private ReporteOperacionesService servicio;

    private static OperacionInfo op(Long id, Long origen, Long destino, String tipo, String monto) {
        return new OperacionInfo(id, origen, destino, tipo, new BigDecimal(monto), "EXITOSA",
                OffsetDateTime.parse("2026-10-0" + id + "T10:00:00Z"), "op " + id);
    }

    private static CuentaInfo cuenta(Long id, Long clienteId, String numero) {
        return new CuentaInfo(id, clienteId, numero, BigDecimal.ZERO, "ACTIVA");
    }

    private static FiltroOperaciones periodo() {
        return new FiltroOperaciones(INICIO, FIN, null, null, null);
    }

    @Test
    void reporteConOperacionesDevuelveResumenAgregadoYDetalleCruzado() {
        given(transaccionApi.buscarOperaciones(DESDE, HASTA, null, null)).willReturn(List.of(
                op(1L, 10L, 20L, "TRANSFERENCIA", "100000.00"),
                op(2L, 10L, null, "CREDITO", "50000.00"),
                op(3L, 20L, 10L, "TRANSFERENCIA", "35000.50")));
        given(cuentaApi.obtenerInfoPorIds(Set.of(10L, 20L))).willReturn(Map.of(
                10L, cuenta(10L, 1L, "1000000001"),
                20L, cuenta(20L, 2L, "2000000002")));
        given(usuarioApi.obtenerClientesPorIds(Set.of(1L, 2L))).willReturn(Map.of(
                1L, new ClienteResumen(1L, "Ana Perez", "CC", "111", "ana@test.local"),
                2L, new ClienteResumen(2L, "Luis Gomez", "CC", "222", "luis@test.local")));

        ReporteOperacionesResponse reporte = servicio.generar(periodo());

        assertThat(reporte.generadoEn()).isNotNull();
        assertThat(reporte.resumen().totalOperaciones()).isEqualTo(3);
        assertThat(reporte.resumen().montoTotal()).isEqualByComparingTo("185000.50");
        assertThat(reporte.resumen().porTipo()).extracting(ReporteOperacionesResponse.TotalPorTipo::tipoTransaccion)
                .containsExactly("CREDITO", "TRANSFERENCIA");
        assertThat(reporte.resumen().porTipo().get(1).cantidad()).isEqualTo(2);
        assertThat(reporte.resumen().porTipo().get(1).montoTotal()).isEqualByComparingTo("135000.50");

        Operacion primera = reporte.operaciones().get(0);
        assertThat(primera.transaccionId()).isEqualTo(1L);
        assertThat(primera.origen().numeroCuenta()).isEqualTo("1000000001");
        assertThat(primera.origen().nombreCliente()).isEqualTo("Ana Perez");
        assertThat(primera.destino().numeroCuenta()).isEqualTo("2000000002");
        assertThat(primera.destino().numeroDocumento()).isEqualTo("222");
        assertThat(reporte.operaciones().get(1).destino()).isNull();
    }

    @Test
    void cadaFuenteSeConsultaUnaSolaVezSinImportarCuantasOperacionesHaya() {
        given(transaccionApi.buscarOperaciones(any(), any(), any(), any())).willReturn(List.of(
                op(1L, 10L, 20L, "TRANSFERENCIA", "1.00"),
                op(2L, 10L, 20L, "TRANSFERENCIA", "1.00"),
                op(3L, 20L, 10L, "TRANSFERENCIA", "1.00"),
                op(4L, 10L, null, "DEBITO", "1.00")));
        given(cuentaApi.obtenerInfoPorIds(anyCollection())).willReturn(Map.of(
                10L, cuenta(10L, 1L, "1000000001"), 20L, cuenta(20L, 2L, "2000000002")));
        given(usuarioApi.obtenerClientesPorIds(anyCollection())).willReturn(Map.of());

        servicio.generar(periodo());

        verify(transaccionApi, times(1)).buscarOperaciones(any(), any(), any(), any());
        verify(cuentaApi, times(1)).obtenerInfoPorIds(anyCollection());
        verify(usuarioApi, times(1)).obtenerClientesPorIds(anyCollection());
    }

    @Test
    void unPeriodoSinOperacionesDevuelveTotalesEnCeroYDetalleVacio() {
        given(transaccionApi.buscarOperaciones(DESDE, HASTA, null, null)).willReturn(List.of());
        given(cuentaApi.obtenerInfoPorIds(Set.of())).willReturn(Map.of());

        ReporteOperacionesResponse reporte = servicio.generar(periodo());

        assertThat(reporte.operaciones()).isEmpty();
        assertThat(reporte.resumen().totalOperaciones()).isZero();
        assertThat(reporte.resumen().montoTotal().toPlainString()).isEqualTo("0.00");
        assertThat(reporte.resumen().porTipo()).isEmpty();
        verifyNoInteractions(usuarioApi);
    }

    @Test
    void unRangoInvertidoFallaCon400SinConsultarNada() {
        assertThatThrownBy(() -> servicio.generar(new FiltroOperaciones(FIN, INICIO, null, null, null)))
                .isInstanceOfSatisfying(NegocioException.class, excepcion -> {
                    assertThat(excepcion.getErrorCode()).isEqualTo("RANGO_FECHAS_INVALIDO");
                    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });

        verifyNoInteractions(transaccionApi, cuentaApi, usuarioApi);
    }

    @Test
    void elFiltroPorClienteSeTraduceASusCuentas() {
        given(cuentaApi.buscarCuentas(1L, null)).willReturn(List.of(
                cuenta(10L, 1L, "1000000001"), cuenta(11L, 1L, "1000000002")));
        given(transaccionApi.buscarOperaciones(DESDE, HASTA, Set.of(10L, 11L), null)).willReturn(List.of());
        given(cuentaApi.obtenerInfoPorIds(Set.of())).willReturn(Map.of());

        servicio.generar(new FiltroOperaciones(INICIO, FIN, 1L, null, null));

        verify(transaccionApi).buscarOperaciones(DESDE, HASTA, Set.of(10L, 11L), null);
    }

    @Test
    void unClienteOCuentaSinCoincidenciasDevuelveUnReporteVacioSinBuscarOperaciones() {
        given(cuentaApi.buscarCuentas(1L, "9999999999")).willReturn(List.of());
        given(cuentaApi.obtenerInfoPorIds(Set.of())).willReturn(Map.of());

        ReporteOperacionesResponse reporte =
                servicio.generar(new FiltroOperaciones(INICIO, FIN, 1L, " 9999999999 ", null));

        assertThat(reporte.operaciones()).isEmpty();
        assertThat(reporte.filtros().numeroCuenta()).isEqualTo("9999999999");
        verify(transaccionApi, never()).buscarOperaciones(any(), any(), any(), any());
    }

    @Test
    void sinFechasNoSeAcotaElPeriodoYElEstadoSeNormaliza() {
        given(transaccionApi.buscarOperaciones(isNull(), isNull(), isNull(), any())).willReturn(List.of());
        given(cuentaApi.obtenerInfoPorIds(Set.of())).willReturn(Map.of());

        ReporteOperacionesResponse reporte = servicio.generar(new FiltroOperaciones(null, null, null, "  ", " exitosa "));

        verify(transaccionApi).buscarOperaciones(null, null, null, "EXITOSA");
        assertThat(reporte.filtros().estado()).isEqualTo("EXITOSA");
        assertThat(reporte.filtros().numeroCuenta()).isNull();
    }

    @Test
    void unaCuentaQueYaNoExisteSaleSinDatosPeroNoRompeElReporte() {
        given(transaccionApi.buscarOperaciones(any(), any(), any(), any()))
                .willReturn(List.of(op(1L, 10L, 99L, "TRANSFERENCIA", "5.00")));
        given(cuentaApi.obtenerInfoPorIds(anyCollection())).willReturn(Map.of(10L, cuenta(10L, 1L, "1000000001")));
        given(usuarioApi.obtenerClientesPorIds(anyCollection())).willReturn(Map.of());

        Operacion operacion = servicio.generar(periodo()).operaciones().get(0);

        assertThat(operacion.origen().numeroCuenta()).isEqualTo("1000000001");
        assertThat(operacion.origen().nombreCliente()).isNull();
        assertThat(operacion.destino()).isNotNull();
        assertThat(operacion.destino().numeroCuenta()).isNull();
    }
}

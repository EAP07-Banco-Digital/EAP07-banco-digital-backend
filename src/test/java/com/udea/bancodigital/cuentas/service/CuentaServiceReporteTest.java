package com.udea.bancodigital.cuentas.service;

import com.udea.bancodigital.cuentas.dto.MovimientoReporteResponse;
import com.udea.bancodigital.cuentas.dto.ReporteMovimientosResponse;
import com.udea.bancodigital.cuentas.entity.Cuenta;
import com.udea.bancodigital.cuentas.entity.TipoCuenta;
import com.udea.bancodigital.cuentas.repository.CuentaRepository;
import com.udea.bancodigital.shared.entity.Estado;
import com.udea.bancodigital.shared.exception.NegocioException;
import com.udea.bancodigital.transferencias.api.TransaccionApi;
import com.udea.bancodigital.transferencias.api.TransaccionApi.TransaccionDetalle;
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
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * HU16: reporte de movimientos por cuenta.
 */
@ExtendWith(MockitoExtension.class)
class CuentaServiceReporteTest {

    private static final Long CLIENTE_ID = 1L;
    private static final Long CUENTA_ID = 10L;
    private static final String NUMERO = "1234567890";
    private static final LocalDate INICIO = LocalDate.of(2026, 10, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 10, 31);
    private static final OffsetDateTime DESDE = OffsetDateTime.parse("2026-10-01T00:00:00-05:00");
    private static final OffsetDateTime HASTA = OffsetDateTime.parse("2026-11-01T00:00:00-05:00");

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private TransaccionApi transaccionApi;

    @InjectMocks
    private CuentaServiceImpl cuentaService;

    private Cuenta cuenta(Long id, Long clienteId, String numero, String saldo) {
        return Cuenta.builder()
                .id(id)
                .clienteId(clienteId)
                .numeroCuenta(numero)
                .tipoCuenta(new TipoCuenta(1L, "AHORROS"))
                .saldoDisponible(new BigDecimal(saldo))
                .estado(new Estado(1L, null, "ACTIVA"))
                .fechaApertura(OffsetDateTime.parse("2026-09-01T10:00:00Z"))
                .build();
    }

    private TransaccionDetalle mov(Long id, Long origen, Long destino, String tipo, String monto, String fecha) {
        return new TransaccionDetalle(id, origen, destino, tipo, new BigDecimal(monto),
                OffsetDateTime.parse(fecha), "mov " + id);
    }

    private void cuentaPropiaConSaldo(String saldo) {
        given(cuentaRepository.findByNumeroCuenta(NUMERO)).willReturn(Optional.of(cuenta(CUENTA_ID, CLIENTE_ID, NUMERO, saldo)));
    }

    @Test
    void reporteDeUnPeriodoConMovimientosCuadraSaldosYTotales() {
        // Saldo actual 130.000; despues del periodo hubo un deposito de 10.000,
        // asi que al cierre del periodo el saldo era 120.000.
        cuentaPropiaConSaldo("130000.00");
        given(transaccionApi.listarMovimientos(CUENTA_ID, DESDE, HASTA)).willReturn(List.of(
                mov(1L, CUENTA_ID, null, "CREDITO", "50000.00", "2026-10-02T10:00:00Z"),
                mov(2L, CUENTA_ID, 20L, "TRANSFERENCIA", "20000.00", "2026-10-10T10:00:00Z"),
                mov(3L, 30L, CUENTA_ID, "TRANSFERENCIA", "15000.00", "2026-10-15T10:00:00Z"),
                mov(4L, CUENTA_ID, null, "DEBITO", "5000.00", "2026-10-20T10:00:00Z")));
        given(transaccionApi.calcularEfectoNetoDesde(CUENTA_ID, HASTA)).willReturn(new BigDecimal("10000.00"));
        given(cuentaRepository.findAllById(Set.of(20L, 30L))).willReturn(List.of(
                cuenta(20L, 2L, "2000000002", "0.00"), cuenta(30L, 3L, "3000000003", "0.00")));

        ReporteMovimientosResponse reporte = cuentaService.generarReporteMovimientos(CLIENTE_ID, NUMERO, INICIO, FIN);

        assertThat(reporte.getNumeroCuenta()).isEqualTo(NUMERO);
        assertThat(reporte.getTipoCuenta()).isEqualTo("AHORROS");
        assertThat(reporte.getFechaInicio()).isEqualTo(INICIO);
        assertThat(reporte.getFechaFin()).isEqualTo(FIN);
        assertThat(reporte.getTotalCreditos()).isEqualByComparingTo("65000.00");
        assertThat(reporte.getTotalDebitos()).isEqualByComparingTo("25000.00");
        assertThat(reporte.getSaldoFinal()).isEqualByComparingTo("120000.00");
        assertThat(reporte.getSaldoInicial()).isEqualByComparingTo("80000.00");
        assertThat(reporte.getSaldoInicial().add(reporte.getTotalCreditos()).subtract(reporte.getTotalDebitos()))
                .isEqualByComparingTo(reporte.getSaldoFinal());
    }

    @Test
    void elDetalleVaEnOrdenCronologicoConNaturalezaYSaldoResultante() {
        cuentaPropiaConSaldo("120000.00");
        given(transaccionApi.listarMovimientos(CUENTA_ID, DESDE, HASTA)).willReturn(List.of(
                mov(1L, CUENTA_ID, null, "CREDITO", "50000.00", "2026-10-02T10:00:00Z"),
                mov(2L, CUENTA_ID, 20L, "TRANSFERENCIA", "20000.00", "2026-10-10T10:00:00Z"),
                mov(3L, 30L, CUENTA_ID, "TRANSFERENCIA", "15000.00", "2026-10-15T10:00:00Z")));
        given(transaccionApi.calcularEfectoNetoDesde(CUENTA_ID, HASTA)).willReturn(BigDecimal.ZERO);
        given(cuentaRepository.findAllById(any())).willReturn(List.of(
                cuenta(20L, 2L, "2000000002", "0.00"), cuenta(30L, 3L, "3000000003", "0.00")));

        List<MovimientoReporteResponse> detalle =
                cuentaService.generarReporteMovimientos(CLIENTE_ID, NUMERO, INICIO, FIN).getTransacciones();

        // Saldo inicial = 120.000 - 65.000 + 20.000 = 75.000
        assertThat(detalle).extracting(MovimientoReporteResponse::getTransaccionId).containsExactly(1L, 2L, 3L);
        assertThat(detalle).extracting(MovimientoReporteResponse::getNaturaleza)
                .containsExactly("CREDITO", "DEBITO", "CREDITO");
        assertThat(detalle).extracting(m -> m.getSaldoResultante().toPlainString())
                .containsExactly("125000.00", "105000.00", "120000.00");
        assertThat(detalle.get(0).getNumeroCuentaOrigen()).isEqualTo(NUMERO);
        assertThat(detalle.get(0).getNumeroCuentaDestino()).isNull();
        assertThat(detalle.get(1).getNumeroCuentaDestino()).isEqualTo("2000000002");
        assertThat(detalle.get(2).getNumeroCuentaOrigen()).isEqualTo("3000000003");
        assertThat(detalle.get(2).getNumeroCuentaDestino()).isEqualTo(NUMERO);
    }

    @Test
    void losNumerosDeLasOtrasCuentasSeBuscanEnUnaSolaConsulta() {
        cuentaPropiaConSaldo("0.00");
        given(transaccionApi.listarMovimientos(CUENTA_ID, DESDE, HASTA)).willReturn(List.of(
                mov(1L, CUENTA_ID, 20L, "TRANSFERENCIA", "1.00", "2026-10-02T10:00:00Z"),
                mov(2L, CUENTA_ID, 20L, "TRANSFERENCIA", "1.00", "2026-10-03T10:00:00Z"),
                mov(3L, 30L, CUENTA_ID, "TRANSFERENCIA", "2.00", "2026-10-04T10:00:00Z")));
        given(transaccionApi.calcularEfectoNetoDesde(CUENTA_ID, HASTA)).willReturn(BigDecimal.ZERO);

        cuentaService.generarReporteMovimientos(CLIENTE_ID, NUMERO, INICIO, FIN);

        verify(cuentaRepository).findAllById(Set.of(20L, 30L));
        verify(cuentaRepository, never()).findById(anyLong());
    }

    @Test
    void unPeriodoSinMovimientosRespondeTotalesEnCeroListaVaciaYSaldosIguales() {
        cuentaPropiaConSaldo("90000.00");
        given(transaccionApi.listarMovimientos(CUENTA_ID, DESDE, HASTA)).willReturn(List.of());
        // Despues del periodo entraron 40.000: el saldo de entonces era 50.000.
        given(transaccionApi.calcularEfectoNetoDesde(CUENTA_ID, HASTA)).willReturn(new BigDecimal("40000.00"));

        ReporteMovimientosResponse reporte = cuentaService.generarReporteMovimientos(CLIENTE_ID, NUMERO, INICIO, FIN);

        assertThat(reporte.getTransacciones()).isEmpty();
        assertThat(reporte.getTotalCreditos()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(reporte.getTotalDebitos()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(reporte.getSaldoInicial()).isEqualByComparingTo("50000.00");
        assertThat(reporte.getSaldoFinal()).isEqualByComparingTo("50000.00");
        verify(cuentaRepository, never()).findAllById(any());
    }

    @Test
    void losValoresMonetariosSalenConDosDecimales() {
        cuentaPropiaConSaldo("0.00");
        given(transaccionApi.listarMovimientos(CUENTA_ID, DESDE, HASTA)).willReturn(List.of());
        given(transaccionApi.calcularEfectoNetoDesde(CUENTA_ID, HASTA)).willReturn(BigDecimal.ZERO);

        ReporteMovimientosResponse reporte = cuentaService.generarReporteMovimientos(CLIENTE_ID, NUMERO, INICIO, FIN);

        assertThat(reporte.getTotalCreditos().toPlainString()).isEqualTo("0.00");
        assertThat(reporte.getTotalDebitos().toPlainString()).isEqualTo("0.00");
        assertThat(reporte.getSaldoInicial().toPlainString()).isEqualTo("0.00");
    }

    @Test
    void unReporteDeUnSoloDiaIncluyeEseDiaCompletoEnHoraDeColombia() {
        cuentaPropiaConSaldo("0.00");
        OffsetDateTime inicioDia = OffsetDateTime.parse("2026-10-05T00:00:00-05:00");
        OffsetDateTime finDia = OffsetDateTime.parse("2026-10-06T00:00:00-05:00");
        given(transaccionApi.listarMovimientos(CUENTA_ID, inicioDia, finDia)).willReturn(List.of());
        given(transaccionApi.calcularEfectoNetoDesde(CUENTA_ID, finDia)).willReturn(BigDecimal.ZERO);

        LocalDate dia = LocalDate.of(2026, 10, 5);
        ReporteMovimientosResponse reporte = cuentaService.generarReporteMovimientos(CLIENTE_ID, NUMERO, dia, dia);

        assertThat(reporte.getTransacciones()).isEmpty();
        verify(transaccionApi).listarMovimientos(CUENTA_ID, inicioDia, finDia);
    }

    @Test
    void unRangoConInicioPosteriorAlFinFallaCon400SinConsultarLaCuenta() {
        assertThatThrownBy(() -> cuentaService.generarReporteMovimientos(CLIENTE_ID, NUMERO, FIN, INICIO))
                .isInstanceOfSatisfying(NegocioException.class, excepcion -> {
                    assertThat(excepcion.getErrorCode()).isEqualTo("RANGO_FECHAS_INVALIDO");
                    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });

        verifyNoInteractions(cuentaRepository, transaccionApi);
    }

    @Test
    void elReporteDeUnaCuentaAjenaFallaCon403SinLeerSusMovimientos() {
        given(cuentaRepository.findByNumeroCuenta(NUMERO)).willReturn(Optional.of(cuenta(CUENTA_ID, 99L, NUMERO, "1.00")));

        assertThatThrownBy(() -> cuentaService.generarReporteMovimientos(CLIENTE_ID, NUMERO, INICIO, FIN))
                .isInstanceOfSatisfying(NegocioException.class, excepcion -> {
                    assertThat(excepcion.getErrorCode()).isEqualTo("FORBIDDEN");
                    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });

        verifyNoInteractions(transaccionApi);
    }

    @Test
    void elReporteDeUnaCuentaInexistenteFallaCon404() {
        given(cuentaRepository.findByNumeroCuenta(NUMERO)).willReturn(Optional.empty());

        assertThatThrownBy(() -> cuentaService.generarReporteMovimientos(CLIENTE_ID, NUMERO, INICIO, FIN))
                .isInstanceOfSatisfying(NegocioException.class, excepcion -> {
                    assertThat(excepcion.getErrorCode()).isEqualTo("ACCOUNT_NOT_FOUND");
                    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    void elReporteSinAutenticacionFallaCon401() {
        assertThatThrownBy(() -> cuentaService.generarReporteMovimientos(null, NUMERO, INICIO, FIN))
                .isInstanceOfSatisfying(NegocioException.class, excepcion ->
                        assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));

        verifyNoInteractions(cuentaRepository, transaccionApi);
    }
}

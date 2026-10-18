package com.udea.bancodigital.transferencias.service;

import com.udea.bancodigital.cuentas.api.CuentaApi;
import com.udea.bancodigital.shared.exception.NegocioException;
import com.udea.bancodigital.transferencias.dto.TransferenciaRequest;
import com.udea.bancodigital.transferencias.dto.TransferenciaResponse;
import com.udea.bancodigital.transferencias.entity.TipoTransaccion;
import com.udea.bancodigital.transferencias.entity.Transaccion;
import com.udea.bancodigital.transferencias.repository.TipoTransaccionRepository;
import com.udea.bancodigital.transferencias.repository.TransaccionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TransferenciaServiceImplTest {

    private static final Long CLIENTE_ID = 7L;
    private static final Long CUENTA_ORIGEN = 1L;
    private static final Long CUENTA_DESTINO = 2L;
    private static final String NUMERO_ORIGEN = "1000000001";
    private static final String NUMERO_DESTINO = "2000000001";
    private static final BigDecimal MONTO = new BigDecimal("100000.00");

    @Mock
    private TransaccionRepository transaccionRepository;

    @Mock
    private TipoTransaccionRepository tipoTransaccionRepository;

    @Mock
    private CuentaApi cuentaApi;

    @InjectMocks
    private TransferenciaServiceImpl transferenciaService;

    private TransferenciaRequest solicitud() {
        TransferenciaRequest request = new TransferenciaRequest();
        request.setNumeroCuentaOrigen(NUMERO_ORIGEN);
        request.setNumeroCuentaDestino(NUMERO_DESTINO);
        request.setMonto(MONTO);
        request.setDescripcion("Pago de prueba");
        return request;
    }

    private CuentaApi.CuentaInfo info(Long id, Long clienteId, String numero, String saldo, String estado) {
        return new CuentaApi.CuentaInfo(id, clienteId, numero, new BigDecimal(saldo), estado);
    }

    private void cuentasListasParaTransferir() {
        given(cuentaApi.obtenerInfoPorNumeroCuenta(NUMERO_ORIGEN))
                .willReturn(info(CUENTA_ORIGEN, CLIENTE_ID, NUMERO_ORIGEN, "500000.00", "ACTIVA"));
        given(cuentaApi.obtenerInfoPorNumeroCuenta(NUMERO_DESTINO))
                .willReturn(info(CUENTA_DESTINO, 99L, NUMERO_DESTINO, "150000.00", "ACTIVA"));
        given(tipoTransaccionRepository.findByNombreIgnoreCase("TRANSFERENCIA"))
                .willReturn(Optional.of(new TipoTransaccion(1L, "TRANSFERENCIA")));
    }

    private void saldoActualizadoEnLasDosCuentas() {
        given(transaccionRepository.save(any(Transaccion.class))).willAnswer(inv -> {
            Transaccion transaccion = inv.getArgument(0);
            transaccion.setId(500L);
            return transaccion;
        });
    }

    @Test
    void transferenciaExitosaDebitaElOrigenYAcreditaElDestino() {
        cuentasListasParaTransferir();
        saldoActualizadoEnLasDosCuentas();

        TransferenciaResponse respuesta = transferenciaService.realizarTransferencia(CLIENTE_ID, solicitud());

        // 500000 - 100000 = 400000
        verify(cuentaApi).actualizarSaldo(CUENTA_ORIGEN, new BigDecimal("400000.00"));
        // 150000 + 100000 = 250000
        verify(cuentaApi).actualizarSaldo(CUENTA_DESTINO, new BigDecimal("250000.00"));
        assertThat(respuesta.getTransaccionId()).isEqualTo(500L);
        assertThat(respuesta.getNumeroCuentaOrigen()).isEqualTo(NUMERO_ORIGEN);
        assertThat(respuesta.getNumeroCuentaDestino()).isEqualTo(NUMERO_DESTINO);
        assertThat(respuesta.getMonto()).isEqualByComparingTo(MONTO);
        assertThat(respuesta.getEstado()).isEqualTo("EXITOSA");
        assertThat(respuesta.getFechaHora()).isNotNull();
    }

    @Test
    void transferenciaRegistraLaTransaccionConSuTipoMontoYDescripcion() {
        cuentasListasParaTransferir();
        saldoActualizadoEnLasDosCuentas();

        transferenciaService.realizarTransferencia(CLIENTE_ID, solicitud());

        ArgumentCaptor<Transaccion> transaccion = ArgumentCaptor.forClass(Transaccion.class);
        verify(transaccionRepository).save(transaccion.capture());
        assertThat(transaccion.getValue().getCuentaOrigenId()).isEqualTo(CUENTA_ORIGEN);
        assertThat(transaccion.getValue().getCuentaDestinoId()).isEqualTo(CUENTA_DESTINO);
        assertThat(transaccion.getValue().getTipoTransaccion().getNombre()).isEqualTo("TRANSFERENCIA");
        assertThat(transaccion.getValue().getMonto()).isEqualByComparingTo(MONTO);
        assertThat(transaccion.getValue().getDescripcion()).isEqualTo("Pago de prueba");
    }

    @Test
    void transferenciaAJuntoLaMismaCuentaFallaCon400() {
        TransferenciaRequest request = solicitud();
        request.setNumeroCuentaDestino(NUMERO_ORIGEN);

        assertThatThrownBy(() -> transferenciaService.realizarTransferencia(CLIENTE_ID, request))
                .isInstanceOfSatisfying(NegocioException.class, excepcion -> {
                    assertThat(excepcion.getErrorCode()).isEqualTo("SAME_ACCOUNT");
                    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });

        verifyNoInteractions(cuentaApi, transaccionRepository);
    }

    @Test
    void transferenciaDesdeUnaCuentaAjenaFallaCon403() {
        given(cuentaApi.obtenerInfoPorNumeroCuenta(NUMERO_ORIGEN))
                .willReturn(info(CUENTA_ORIGEN, 99L, NUMERO_ORIGEN, "500000.00", "ACTIVA"));

        assertThatThrownBy(() -> transferenciaService.realizarTransferencia(CLIENTE_ID, solicitud()))
                .isInstanceOfSatisfying(NegocioException.class, excepcion -> {
                    assertThat(excepcion.getErrorCode()).isEqualTo("FORBIDDEN");
                    assertThat(excepcion.getMessage()).contains("autenticado");
                    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });

        verify(cuentaApi, never()).actualizarSaldo(anyLong(), any());
        verifyNoInteractions(transaccionRepository);
    }

    @Test
    void transferenciaDesdeUnaCuentaBloqueadaFallaCon409() {
        given(cuentaApi.obtenerInfoPorNumeroCuenta(NUMERO_ORIGEN))
                .willReturn(info(CUENTA_ORIGEN, CLIENTE_ID, NUMERO_ORIGEN, "500000.00", "BLOQUEADA"));

        assertThatThrownBy(() -> transferenciaService.realizarTransferencia(CLIENTE_ID, solicitud()))
                .isInstanceOfSatisfying(NegocioException.class, excepcion -> {
                    assertThat(excepcion.getErrorCode()).isEqualTo("ACCOUNT_INACTIVE");
                    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });

        verify(cuentaApi, never()).actualizarSaldo(anyLong(), any());
    }

    @Test
    void transferenciaHaciaUnaCuentaBloqueadaFallaCon409() {
        given(cuentaApi.obtenerInfoPorNumeroCuenta(NUMERO_ORIGEN))
                .willReturn(info(CUENTA_ORIGEN, CLIENTE_ID, NUMERO_ORIGEN, "500000.00", "ACTIVA"));
        given(cuentaApi.obtenerInfoPorNumeroCuenta(NUMERO_DESTINO))
                .willReturn(info(CUENTA_DESTINO, 99L, NUMERO_DESTINO, "150000.00", "BLOQUEADA"));

        assertThatThrownBy(() -> transferenciaService.realizarTransferencia(CLIENTE_ID, solicitud()))
                .isInstanceOfSatisfying(NegocioException.class, excepcion -> {
                    assertThat(excepcion.getErrorCode()).isEqualTo("DESTINATION_ACCOUNT_INACTIVE");
                    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });

        verify(cuentaApi, never()).actualizarSaldo(anyLong(), any());
    }

    @Test
    void transferenciaSinSaldoSuficienteFallaCon400YTocaNingunSaldo() {
        given(cuentaApi.obtenerInfoPorNumeroCuenta(NUMERO_ORIGEN))
                .willReturn(info(CUENTA_ORIGEN, CLIENTE_ID, NUMERO_ORIGEN, "50000.00", "ACTIVA"));
        given(cuentaApi.obtenerInfoPorNumeroCuenta(NUMERO_DESTINO))
                .willReturn(info(CUENTA_DESTINO, 99L, NUMERO_DESTINO, "150000.00", "ACTIVA"));

        assertThatThrownBy(() -> transferenciaService.realizarTransferencia(CLIENTE_ID, solicitud()))
                .isInstanceOfSatisfying(NegocioException.class, excepcion -> {
                    assertThat(excepcion.getErrorCode()).isEqualTo("INSUFFICIENT_FUNDS");
                    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });

        // Es el punto critico del modulo: un rechazo no puede mover ni un centavo.
        verify(cuentaApi, never()).actualizarSaldo(anyLong(), any());
        verify(transaccionRepository, never()).save(any(Transaccion.class));
    }

    @Test
    void transferirTodoElSaldoDisponibleEsValido() {
        // Frontera de la regla de fondos: saldo == monto, no saldo < monto.
        TransferenciaRequest request = solicitud();
        request.setMonto(new BigDecimal("500000.00"));
        given(cuentaApi.obtenerInfoPorNumeroCuenta(NUMERO_ORIGEN))
                .willReturn(info(CUENTA_ORIGEN, CLIENTE_ID, NUMERO_ORIGEN, "500000.00", "ACTIVA"));
        given(cuentaApi.obtenerInfoPorNumeroCuenta(NUMERO_DESTINO))
                .willReturn(info(CUENTA_DESTINO, 99L, NUMERO_DESTINO, "150000.00", "ACTIVA"));
        given(tipoTransaccionRepository.findByNombreIgnoreCase("TRANSFERENCIA"))
                .willReturn(Optional.of(new TipoTransaccion(1L, "TRANSFERENCIA")));
        saldoActualizadoEnLasDosCuentas();

        transferenciaService.realizarTransferencia(CLIENTE_ID, request);

        verify(cuentaApi).actualizarSaldo(CUENTA_ORIGEN, new BigDecimal("0.00"));
        verify(cuentaApi).actualizarSaldo(CUENTA_DESTINO, new BigDecimal("650000.00"));
    }

    @Test
    void transferenciaDescuentaElMontoExactoConDecimales() {
        TransferenciaRequest request = solicitud();
        request.setMonto(new BigDecimal("100.555"));
        given(cuentaApi.obtenerInfoPorNumeroCuenta(NUMERO_ORIGEN))
                .willReturn(info(CUENTA_ORIGEN, CLIENTE_ID, NUMERO_ORIGEN, "1000.00", "ACTIVA"));
        given(cuentaApi.obtenerInfoPorNumeroCuenta(NUMERO_DESTINO))
                .willReturn(info(CUENTA_DESTINO, 99L, NUMERO_DESTINO, "0.00", "ACTIVA"));
        given(tipoTransaccionRepository.findByNombreIgnoreCase("TRANSFERENCIA"))
                .willReturn(Optional.of(new TipoTransaccion(1L, "TRANSFERENCIA")));
        saldoActualizadoEnLasDosCuentas();

        transferenciaService.realizarTransferencia(CLIENTE_ID, request);

        // Aritmetica decimal, no de punto flotante: 1000.00 - 100.555 = 899.445.
        verify(cuentaApi).actualizarSaldo(CUENTA_ORIGEN, new BigDecimal("899.445"));
        verify(cuentaApi).actualizarSaldo(CUENTA_DESTINO, new BigDecimal("100.555"));
    }

    @Test
    void transferenciaSinAutenticacionFallaCon401() {
        assertThatThrownBy(() -> transferenciaService.realizarTransferencia(null, solicitud()))
                .isInstanceOfSatisfying(NegocioException.class, excepcion -> {
                    assertThat(excepcion.getErrorCode()).isEqualTo("UNAUTHORIZED");
                    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });

        verifyNoInteractions(cuentaApi, transaccionRepository, tipoTransaccionRepository);
    }

    @Test
    void transferenciaHaciaUnaCuentaInexistentePropagaElErrorYTocaNingunSaldo() {
        given(cuentaApi.obtenerInfoPorNumeroCuenta(NUMERO_ORIGEN))
                .willReturn(info(CUENTA_ORIGEN, CLIENTE_ID, NUMERO_ORIGEN, "500000.00", "ACTIVA"));
        given(cuentaApi.obtenerInfoPorNumeroCuenta(NUMERO_DESTINO))
                .willThrow(new NegocioException("DESTINATION_ACCOUNT_NOT_FOUND",
                        "La cuenta " + NUMERO_DESTINO + " no existe", HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> transferenciaService.realizarTransferencia(CLIENTE_ID, solicitud()))
                .isInstanceOfSatisfying(NegocioException.class, excepcion -> {
                    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(excepcion.getMessage()).contains(NUMERO_DESTINO);
                });

        verify(cuentaApi, never()).actualizarSaldo(anyLong(), any());
    }

    @Test
    void sinElTipoTransaccionEnCatalogoFallaCon500DespuesDeMoverLosSaldos() {
        // El guardado de la transaccion es el ultimo paso: si falla, los saldos
        // ya se habian escrito. Lo que los revierte es el @Transactional del
        // metodo, no esta logica: aqui solo se constata el orden de las operaciones.
        given(cuentaApi.obtenerInfoPorNumeroCuenta(NUMERO_ORIGEN))
                .willReturn(info(CUENTA_ORIGEN, CLIENTE_ID, NUMERO_ORIGEN, "500000.00", "ACTIVA"));
        given(cuentaApi.obtenerInfoPorNumeroCuenta(NUMERO_DESTINO))
                .willReturn(info(CUENTA_DESTINO, 99L, NUMERO_DESTINO, "150000.00", "ACTIVA"));
        given(tipoTransaccionRepository.findByNombreIgnoreCase("TRANSFERENCIA")).willReturn(Optional.empty());

        assertThatThrownBy(() -> transferenciaService.realizarTransferencia(CLIENTE_ID, solicitud()))
                .isInstanceOfSatisfying(NegocioException.class, excepcion -> {
                    assertThat(excepcion.getErrorCode()).isEqualTo("INTERNAL_ERROR");
                    assertThat(excepcion.getMessage()).contains("TRANSFERENCIA");
                    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                });

        verify(cuentaApi).actualizarSaldo(CUENTA_ORIGEN, new BigDecimal("400000.00"));
        verify(cuentaApi).actualizarSaldo(CUENTA_DESTINO, new BigDecimal("250000.00"));
        verify(transaccionRepository, never()).save(any(Transaccion.class));
    }

    @Test
    void losSaldosSeActualizanAntesDeRegistrarLaTransaccion() {
        cuentasListasParaTransferir();
        saldoActualizadoEnLasDosCuentas();

        transferenciaService.realizarTransferencia(CLIENTE_ID, solicitud());

        // Debitar -> acreditar -> registrar. Al revés, una caida entre pasos
        // dejaria el dinero debitado sin rastro de la operacion.
        var orden = inOrder(cuentaApi, transaccionRepository);
        orden.verify(cuentaApi).actualizarSaldo(CUENTA_ORIGEN, new BigDecimal("400000.00"));
        orden.verify(cuentaApi).actualizarSaldo(CUENTA_DESTINO, new BigDecimal("250000.00"));
        orden.verify(transaccionRepository).save(any(Transaccion.class));
    }

    @Test
    void elEstadoDeLaRespuestaSiempreEsFijoExitosa() {
        // No existe estado FALLIDA: una operacion rechazada no deja registro
        // (DEF-HU12-05). Lo que se ve en la respuesta es siempre EXITOSA.
        cuentasListasParaTransferir();
        saldoActualizadoEnLasDosCuentas();

        assertThat(transferenciaService.realizarTransferencia(CLIENTE_ID, solicitud()).getEstado())
                .isEqualTo("EXITOSA");
    }
}

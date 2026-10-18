package com.udea.bancodigital.cuentas.service;

import com.udea.bancodigital.cuentas.dto.AperturaCuentaRequest;
import com.udea.bancodigital.cuentas.dto.CuentaResponse;
import com.udea.bancodigital.cuentas.entity.Cuenta;
import com.udea.bancodigital.cuentas.entity.TipoCuenta;
import com.udea.bancodigital.cuentas.repository.CuentaRepository;
import com.udea.bancodigital.cuentas.repository.TipoCuentaRepository;
import com.udea.bancodigital.shared.entity.Estado;
import com.udea.bancodigital.shared.exception.NegocioException;
import com.udea.bancodigital.shared.repository.EstadoRepository;
import com.udea.bancodigital.cuentas.api.CuentaApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CuentaServiceImplTest {

    private static final Long CLIENTE_ID = 7L;
    private static final String NUMERO = "1000000001";

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private TipoCuentaRepository tipoCuentaRepository;

    @Mock
    private EstadoRepository estadoRepository;

    @InjectMocks
    private CuentaServiceImpl cuentaService;

    private TipoCuenta tipo(String nombre) {
        return TipoCuenta.builder().id(1L).nombre(nombre).build();
    }

    private Estado estado(String nombre) {
        return new Estado(1L, null, nombre);
    }

    private Cuenta cuenta(Long id, Long clienteId, String numero, TipoCuenta tipoCuenta,
                          String saldo, String nombreEstado) {
        return Cuenta.builder()
                .id(id)
                .clienteId(clienteId)
                .numeroCuenta(numero)
                .tipoCuenta(tipoCuenta)
                .saldoDisponible(new BigDecimal(saldo))
                .estado(estado(nombreEstado))
                .fechaApertura(OffsetDateTime.parse("2026-09-20T10:00:00-05:00"))
                .build();
    }

    // ------------------------------------------------------------- HU5 apertura

    @Test
    void aperturaExitosaCreaLaCuentaConSaldoCeroYEstadoActiva() {
        given(tipoCuentaRepository.findByNombreIgnoreCase("AHORROS")).willReturn(Optional.of(tipo("AHORROS")));
        given(cuentaRepository.existsCuentaActivaPorClienteYTipo(CLIENTE_ID, "AHORROS")).willReturn(false);
        given(estadoRepository.findByNombreIgnoreCase("ACTIVA")).willReturn(Optional.of(estado("ACTIVA")));
        given(cuentaRepository.save(any(Cuenta.class))).willAnswer(inv -> {
            Cuenta cuenta = inv.getArgument(0);
            cuenta.setId(1L);
            return cuenta;
        });

        CuentaResponse respuesta = cuentaService.solicitarAperturaCuenta(CLIENTE_ID, new AperturaCuentaRequest("AHORROS"));

        assertThat(respuesta.getId()).isEqualTo(1L);
        assertThat(respuesta.getClienteId()).isEqualTo(CLIENTE_ID);
        assertThat(respuesta.getTipoCuenta()).isEqualTo("AHORROS");
        assertThat(respuesta.getSaldoDisponible()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(respuesta.getEstado()).isEqualTo("ACTIVA");
    }

    @Test
    void aperturaGeneraUnNumeroDeCuentaDeDiezDigitosYUnico() {
        given(tipoCuentaRepository.findByNombreIgnoreCase("CORRIENTE")).willReturn(Optional.of(tipo("CORRIENTE")));
        given(cuentaRepository.existsCuentaActivaPorClienteYTipo(CLIENTE_ID, "CORRIENTE")).willReturn(false);
        given(estadoRepository.findByNombreIgnoreCase("ACTIVA")).willReturn(Optional.of(estado("ACTIVA")));

        ArgumentCaptor<Cuenta> guardada = ArgumentCaptor.forClass(Cuenta.class);
        given(cuentaRepository.save(guardada.capture())).willAnswer(inv -> {
            Cuenta cuenta = inv.getArgument(0);
            cuenta.setId(1L);
            return cuenta;
        });

        cuentaService.solicitarAperturaCuenta(CLIENTE_ID, new AperturaCuentaRequest("CORRIENTE"));

        assertThat(guardada.getValue().getNumeroCuenta()).hasSize(10).containsOnlyDigits();
    }

    @Test
    void apertureReintentaConOtroNumeroCuandoElGeneradoYaExiste() {
        given(tipoCuentaRepository.findByNombreIgnoreCase("AHORROS")).willReturn(Optional.of(tipo("AHORROS")));
        given(cuentaRepository.existsCuentaActivaPorClienteYTipo(CLIENTE_ID, "AHORROS")).willReturn(false);
        given(estadoRepository.findByNombreIgnoreCase("ACTIVA")).willReturn(Optional.of(estado("ACTIVA")));
        // El primer numero sale repetido, el segundo ya es libre.
        willReturn(true, false).given(cuentaRepository).existsByNumeroCuenta(anyString());

        ArgumentCaptor<Cuenta> guardada = ArgumentCaptor.forClass(Cuenta.class);
        given(cuentaRepository.save(guardada.capture())).willAnswer(inv -> {
            Cuenta cuenta = inv.getArgument(0);
            cuenta.setId(1L);
            return cuenta;
        });

        cuentaService.solicitarAperturaCuenta(CLIENTE_ID, new AperturaCuentaRequest("AHORROS"));

        verify(cuentaRepository, times(2)).existsByNumeroCuenta(anyString());
        assertThat(guardada.getValue().getNumeroCuenta()).hasSize(10);
    }

    @Test
    void apertureNormalizaElTipoAMayusculasYConEspacios() {
        given(tipoCuentaRepository.findByNombreIgnoreCase("AHORROS")).willReturn(Optional.of(tipo("AHORROS")));
        given(cuentaRepository.existsCuentaActivaPorClienteYTipo(CLIENTE_ID, "AHORROS")).willReturn(false);
        given(estadoRepository.findByNombreIgnoreCase("ACTIVA")).willReturn(Optional.of(estado("ACTIVA")));
        given(cuentaRepository.save(any(Cuenta.class))).willAnswer(inv -> {
            Cuenta cuenta = inv.getArgument(0);
            cuenta.setId(1L);
            return cuenta;
        });

        cuentaService.solicitarAperturaCuenta(CLIENTE_ID, new AperturaCuentaRequest("  ahorros "));

        verify(tipoCuentaRepository).findByNombreIgnoreCase("AHORROS");
    }

    @Test
    void aperturaConTipoDeCuentaInvalidoFallaCon400() {
        given(tipoCuentaRepository.findByNombreIgnoreCase("PLAJO")).willReturn(Optional.empty());

        assertThatThrownBy(() -> cuentaService.solicitarAperturaCuenta(CLIENTE_ID, new AperturaCuentaRequest("PLAJO")))
                .isInstanceOfSatisfying(NegocioException.class, excepcion -> {
                    assertThat(excepcion.getErrorCode()).isEqualTo("INVALID_ACCOUNT_TYPE");
                    assertThat(excepcion.getMessage()).contains("PLAJO");
                    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });

        verify(cuentaRepository, never()).save(any(Cuenta.class));
    }

    @Test
    void aperturaDeUnTipoQueElClienteYaPoseeFallaCon409() {
        given(tipoCuentaRepository.findByNombreIgnoreCase("AHORROS")).willReturn(Optional.of(tipo("AHORROS")));
        given(cuentaRepository.existsCuentaActivaPorClienteYTipo(CLIENTE_ID, "AHORROS")).willReturn(true);

        assertThatThrownBy(() -> cuentaService.solicitarAperturaCuenta(CLIENTE_ID, new AperturaCuentaRequest("AHORROS")))
                .isInstanceOfSatisfying(NegocioException.class, excepcion -> {
                    assertThat(excepcion.getErrorCode()).isEqualTo("ACCOUNT_TYPE_ALREADY_EXISTS");
                    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });

        verify(cuentaRepository, never()).save(any(Cuenta.class));
    }

    @Test
    void aperturaSinAutenticacionFallaCon401() {
        assertThatThrownBy(() -> cuentaService.solicitarAperturaCuenta(null, new AperturaCuentaRequest("AHORROS")))
                .isInstanceOfSatisfying(NegocioException.class, excepcion -> {
                    assertThat(excepcion.getErrorCode()).isEqualTo("UNAUTHORIZED");
                    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });

        verifyNoInteractions(cuentaRepository, tipoCuentaRepository, estadoRepository);
    }

    @Test
    void aperturaSinElEstadoActivaEnCatalogoFallaCon500() {
        given(tipoCuentaRepository.findByNombreIgnoreCase("AHORROS")).willReturn(Optional.of(tipo("AHORROS")));
        given(cuentaRepository.existsCuentaActivaPorClienteYTipo(CLIENTE_ID, "AHORROS")).willReturn(false);
        given(estadoRepository.findByNombreIgnoreCase("ACTIVA")).willReturn(Optional.empty());

        assertThatThrownBy(() -> cuentaService.solicitarAperturaCuenta(CLIENTE_ID, new AperturaCuentaRequest("AHORROS")))
                .isInstanceOfSatisfying(NegocioException.class, excepcion -> {
                    assertThat(excepcion.getErrorCode()).isEqualTo("INTERNAL_ERROR");
                    assertThat(excepcion.getMessage()).contains("ACTIVA");
                    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                });

        verify(cuentaRepository, never()).save(any(Cuenta.class));
    }

    // ------------------------------------------------------------- HU6 consulta

    @Test
    void consultaDelSaldoDeUnaCuentaPropiaDevuelveSaldoYEstado() {
        given(cuentaRepository.findByNumeroCuenta(NUMERO))
                .willReturn(Optional.of(cuenta(1L, CLIENTE_ID, NUMERO, tipo("AHORROS"), "500000.00", "ACTIVA")));

        CuentaResponse respuesta = cuentaService.consultarSaldo(CLIENTE_ID, NUMERO);

        assertThat(respuesta.getSaldoDisponible()).isEqualByComparingTo("500000.00");
        assertThat(respuesta.getEstado()).isEqualTo("ACTIVA");
        assertThat(respuesta.getTipoCuenta()).isEqualTo("AHORROS");
    }

    @Test
    void consultaDelSaldoDeUnaCuentaBloqueadaRespondeConSuEstadoYNoFalla() {
        // Criterio de la HU6: una cuenta bloqueada se consulta igual, con 200.
        given(cuentaRepository.findByNumeroCuenta(NUMERO))
                .willReturn(Optional.of(cuenta(3L, CLIENTE_ID, NUMERO, tipo("AHORROS"), "250000.00", "BLOQUEADA")));

        CuentaResponse respuesta = cuentaService.consultarSaldo(CLIENTE_ID, NUMERO);

        assertThat(respuesta.getEstado()).isEqualTo("BLOQUEADA");
        assertThat(respuesta.getSaldoDisponible()).isEqualByComparingTo("250000.00");
    }

    @Test
    void consultaDelSaldoDeUnaCuentaAjenaFallaCon403() {
        given(cuentaRepository.findByNumeroCuenta(NUMERO))
                .willReturn(Optional.of(cuenta(5L, 99L, NUMERO, tipo("AHORROS"), "80000.00", "ACTIVA")));

        assertThatThrownBy(() -> cuentaService.consultarSaldo(CLIENTE_ID, NUMERO))
                .isInstanceOfSatisfying(NegocioException.class, excepcion -> {
                    assertThat(excepcion.getErrorCode()).isEqualTo("FORBIDDEN");
                    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    void consultaDelSaldoDeUnaCuentaInexistenteFallaCon404() {
        given(cuentaRepository.findByNumeroCuenta("9999999999")).willReturn(Optional.empty());

        assertThatThrownBy(() -> cuentaService.consultarSaldo(CLIENTE_ID, "9999999999"))
                .isInstanceOfSatisfying(NegocioException.class, excepcion -> {
                    assertThat(excepcion.getErrorCode()).isEqualTo("ACCOUNT_NOT_FOUND");
                    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    void consultaDelSaldoSinAutenticacionFallaCon401() {
        assertThatThrownBy(() -> cuentaService.consultarSaldo(null, NUMERO))
                .isInstanceOfSatisfying(NegocioException.class, excepcion ->
                        assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));

        verify(cuentaRepository, never()).findByNumeroCuenta(anyString());
    }

    // ------------------------------------------------------ API publica cuentas

    @Test
    void obtenerInfoPorNumeroCuentaDevuelveLosDatosParaOtrosModulos() {
        given(cuentaRepository.findByNumeroCuenta(NUMERO))
                .willReturn(Optional.of(cuenta(1L, CLIENTE_ID, NUMERO, tipo("AHORROS"), "500000.00", "ACTIVA")));

        CuentaApi.CuentaInfo info = cuentaService.obtenerInfoPorNumeroCuenta(NUMERO);

        assertThat(info.id()).isEqualTo(1L);
        assertThat(info.clienteId()).isEqualTo(CLIENTE_ID);
        assertThat(info.numeroCuenta()).isEqualTo(NUMERO);
        assertThat(info.saldoDisponible()).isEqualByComparingTo("500000.00");
        assertThat(info.estadoNombre()).isEqualTo("ACTIVA");
    }

    @Test
    void obtenerInfoDeUnaCuentaInexistenteFallaCon404IndicandoElNumero() {
        given(cuentaRepository.findByNumeroCuenta("9999999999")).willReturn(Optional.empty());

        assertThatThrownBy(() -> cuentaService.obtenerInfoPorNumeroCuenta("9999999999"))
                .isInstanceOfSatisfying(NegocioException.class, excepcion -> {
                    assertThat(excepcion.getErrorCode()).isEqualTo("DESTINATION_ACCOUNT_NOT_FOUND");
                    assertThat(excepcion.getMessage()).contains("9999999999");
                    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    void actualizarSaldoFijaElNuevoValorEnLaCuenta() {
        Cuenta cuenta = cuenta(1L, CLIENTE_ID, NUMERO, tipo("AHORROS"), "500000.00", "ACTIVA");
        given(cuentaRepository.findById(1L)).willReturn(Optional.of(cuenta));

        cuentaService.actualizarSaldo(1L, new BigDecimal("250000.00"));

        assertThat(cuenta.getSaldoDisponible()).isEqualByComparingTo("250000.00");
        verify(cuentaRepository).save(cuenta);
    }

    @Test
    void actualizarSaldoDeUnaCuentaQueDesaparecioFallaCon500() {
        given(cuentaRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> cuentaService.actualizarSaldo(1L, BigDecimal.ONE))
                .isInstanceOfSatisfying(NegocioException.class, excepcion -> {
                    assertThat(excepcion.getErrorCode()).isEqualTo("INTERNAL_ERROR");
                    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                });

        verify(cuentaRepository, never()).save(any(Cuenta.class));
    }
}

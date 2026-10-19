package com.udea.bancodigital.cuentas.api;

import com.udea.bancodigital.cuentas.entity.Cuenta;
import com.udea.bancodigital.cuentas.entity.TipoCuenta;
import com.udea.bancodigital.cuentas.repository.CuentaRepository;
import com.udea.bancodigital.shared.entity.Estado;
import com.udea.bancodigital.shared.exception.NegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests de la API pública inter-módulos de Cuentas (CuentaApiImpl).
 * <p>
 * Estos casos se separaron de CuentaServiceImplTest cuando los métodos
 * obtenerInfoPorNumeroCuenta y actualizarSaldo fueron extraídos de
 * CuentaServiceImpl a CuentaApiImpl para resolver la dependencia circular.
 */
@ExtendWith(MockitoExtension.class)
class CuentaApiImplTest {

    private static final Long CLIENTE_ID = 7L;
    private static final String NUMERO = "1000000001";

    @Mock
    private CuentaRepository cuentaRepository;

    @InjectMocks
    private CuentaApiImpl cuentaApi;

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

    @Test
    void obtenerInfoPorNumeroCuentaDevuelveLosDatosParaOtrosModulos() {
        given(cuentaRepository.findByNumeroCuenta(NUMERO))
                .willReturn(Optional.of(cuenta(1L, CLIENTE_ID, NUMERO, tipo("AHORROS"), "500000.00", "ACTIVA")));

        CuentaApi.CuentaInfo info = cuentaApi.obtenerInfoPorNumeroCuenta(NUMERO);

        assertThat(info.id()).isEqualTo(1L);
        assertThat(info.clienteId()).isEqualTo(CLIENTE_ID);
        assertThat(info.numeroCuenta()).isEqualTo(NUMERO);
        assertThat(info.saldoDisponible()).isEqualByComparingTo("500000.00");
        assertThat(info.estadoNombre()).isEqualTo("ACTIVA");
    }

    @Test
    void obtenerInfoDeUnaCuentaInexistenteFallaCon404IndicandoElNumero() {
        given(cuentaRepository.findByNumeroCuenta("9999999999")).willReturn(Optional.empty());

        assertThatThrownBy(() -> cuentaApi.obtenerInfoPorNumeroCuenta("9999999999"))
                .isInstanceOfSatisfying(NegocioException.class, excepcion -> {
                    assertThat(excepcion.getErrorCode()).isEqualTo("DESTINATION_ACCOUNT_NOT_FOUND");
                    assertThat(excepcion.getMessage()).contains("9999999999");
                    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    void bloquearPorNumeroCuentaLeeLaFilaConBloqueoYNoSinEl() {
        given(cuentaRepository.findByNumeroCuentaForUpdate(NUMERO))
                .willReturn(Optional.of(cuenta(1L, CLIENTE_ID, NUMERO, tipo("AHORROS"), "500000.00", "ACTIVA")));

        CuentaApi.CuentaInfo info = cuentaApi.bloquearPorNumeroCuenta(NUMERO);

        assertThat(info.id()).isEqualTo(1L);
        assertThat(info.saldoDisponible()).isEqualByComparingTo("500000.00");
        verify(cuentaRepository, never()).findByNumeroCuenta(anyString());
    }

    @Test
    void bloquearUnaCuentaInexistenteFallaCon404IndicandoElNumero() {
        given(cuentaRepository.findByNumeroCuentaForUpdate("9999999999")).willReturn(Optional.empty());

        assertThatThrownBy(() -> cuentaApi.bloquearPorNumeroCuenta("9999999999"))
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

        cuentaApi.actualizarSaldo(1L, new BigDecimal("250000.00"));

        assertThat(cuenta.getSaldoDisponible()).isEqualByComparingTo("250000.00");
        verify(cuentaRepository).save(cuenta);
    }

    @Test
    void actualizarSaldoDeUnaCuentaQueDesaparecioFallaCon500() {
        given(cuentaRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> cuentaApi.actualizarSaldo(1L, BigDecimal.ONE))
                .isInstanceOfSatisfying(NegocioException.class, excepcion -> {
                    assertThat(excepcion.getErrorCode()).isEqualTo("INTERNAL_ERROR");
                    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                });

        verify(cuentaRepository, never()).save(any(Cuenta.class));
    }
}

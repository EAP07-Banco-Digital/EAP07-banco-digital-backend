package com.udea.bancodigital.cuentas.service;

import com.udea.bancodigital.cuentas.dto.OperacionCuentaRequest;
import com.udea.bancodigital.cuentas.dto.OperacionCuentaResponse;
import com.udea.bancodigital.cuentas.entity.Cuenta;
import com.udea.bancodigital.cuentas.entity.TipoCuenta;
import com.udea.bancodigital.cuentas.repository.CuentaRepository;
import com.udea.bancodigital.cuentas.repository.TipoCuentaRepository;
import com.udea.bancodigital.shared.entity.Estado;
import com.udea.bancodigital.shared.entity.TipoEstado;
import com.udea.bancodigital.shared.exception.NegocioException;
import com.udea.bancodigital.shared.repository.EstadoRepository;
import com.udea.bancodigital.transferencias.api.TransaccionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CuentaServiceOperacionesTest {

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private TipoCuentaRepository tipoCuentaRepository;

    @Mock
    private EstadoRepository estadoRepository;

    @Mock
    private TransaccionApi transaccionApi;

    @InjectMocks
    private CuentaServiceImpl cuentaService;

    private final Long CLIENTE_ID = 1L;
    private final String NUMERO_CUENTA = "1234567890";
    private Estado estadoActiva;
    private Estado estadoBloqueada;
    private Cuenta cuentaActiva;

    @BeforeEach
    void setUp() {
        TipoEstado tipoEstadoCuenta = new TipoEstado(1L, "CUENTA");
        estadoActiva = new Estado(1L, tipoEstadoCuenta, "ACTIVA");
        estadoBloqueada = new Estado(2L, tipoEstadoCuenta, "BLOQUEADA");

        cuentaActiva = Cuenta.builder()
                .id(10L)
                .clienteId(CLIENTE_ID)
                .numeroCuenta(NUMERO_CUENTA)
                .tipoCuenta(new TipoCuenta(1L, "AHORROS"))
                .saldoDisponible(new BigDecimal("100000.00"))
                .estado(estadoActiva)
                .fechaApertura(OffsetDateTime.now())
                .build();
    }

    @Test
    void registrarCredito_exitoso_incrementaSaldoYRegistraTransaccion() {
        OperacionCuentaRequest request = new OperacionCuentaRequest(new BigDecimal("50000.00"));
        TransaccionApi.TransaccionInfo infoMock = new TransaccionApi.TransaccionInfo(201L, OffsetDateTime.now());

        given(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).willReturn(Optional.of(cuentaActiva));
        given(transaccionApi.registrarTransaccion(eq(10L), isNull(), eq("CREDITO"), eq(new BigDecimal("50000.00")), anyString()))
                .willReturn(infoMock);

        OperacionCuentaResponse response = cuentaService.registrarCredito(CLIENTE_ID, NUMERO_CUENTA, request);

        assertNotNull(response);
        assertEquals(201L, response.getTransaccionId());
        assertEquals(NUMERO_CUENTA, response.getNumeroCuenta());
        assertEquals(new BigDecimal("50000.00"), response.getMonto());
        assertEquals(new BigDecimal("150000.00"), response.getSaldoNuevo());
        assertEquals("EXITOSA", response.getEstado());
        assertEquals(new BigDecimal("150000.00"), cuentaActiva.getSaldoDisponible());

        verify(cuentaRepository).save(cuentaActiva);
        verify(transaccionApi).registrarTransaccion(eq(10L), isNull(), eq("CREDITO"), eq(new BigDecimal("50000.00")), anyString());
    }

    @Test
    void registrarDebito_exitoso_disminuyeSaldoYRegistraTransaccion() {
        OperacionCuentaRequest request = new OperacionCuentaRequest(new BigDecimal("40000.00"));
        TransaccionApi.TransaccionInfo infoMock = new TransaccionApi.TransaccionInfo(202L, OffsetDateTime.now());

        given(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).willReturn(Optional.of(cuentaActiva));
        given(transaccionApi.registrarTransaccion(eq(10L), isNull(), eq("DEBITO"), eq(new BigDecimal("40000.00")), anyString()))
                .willReturn(infoMock);

        OperacionCuentaResponse response = cuentaService.registrarDebito(CLIENTE_ID, NUMERO_CUENTA, request);

        assertNotNull(response);
        assertEquals(202L, response.getTransaccionId());
        assertEquals(NUMERO_CUENTA, response.getNumeroCuenta());
        assertEquals(new BigDecimal("40000.00"), response.getMonto());
        assertEquals(new BigDecimal("60000.00"), response.getSaldoNuevo());
        assertEquals("EXITOSA", response.getEstado());
        assertEquals(new BigDecimal("60000.00"), cuentaActiva.getSaldoDisponible());

        verify(cuentaRepository).save(cuentaActiva);
        verify(transaccionApi).registrarTransaccion(eq(10L), isNull(), eq("DEBITO"), eq(new BigDecimal("40000.00")), anyString());
    }

    @Test
    void registrarDebito_sinSaldoSuficiente_lanzaInsufficientFunds() {
        OperacionCuentaRequest request = new OperacionCuentaRequest(new BigDecimal("150000.00"));
        given(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).willReturn(Optional.of(cuentaActiva));

        NegocioException ex = assertThrows(NegocioException.class,
                () -> cuentaService.registrarDebito(CLIENTE_ID, NUMERO_CUENTA, request));

        assertEquals("INSUFFICIENT_FUNDS", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals(new BigDecimal("100000.00"), cuentaActiva.getSaldoDisponible());
        verify(cuentaRepository, never()).save(any());
        verify(transaccionApi, never()).registrarTransaccion(any(), any(), any(), any(), any());
    }

    @Test
    void registrarCredito_montoCeroONegativo_lanzaInvalidAmount() {
        OperacionCuentaRequest requestCero = new OperacionCuentaRequest(BigDecimal.ZERO);
        OperacionCuentaRequest requestNegativo = new OperacionCuentaRequest(new BigDecimal("-50.00"));

        NegocioException exCero = assertThrows(NegocioException.class,
                () -> cuentaService.registrarCredito(CLIENTE_ID, NUMERO_CUENTA, requestCero));
        assertEquals("INVALID_AMOUNT", exCero.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, exCero.getStatus());

        NegocioException exNeg = assertThrows(NegocioException.class,
                () -> cuentaService.registrarDebito(CLIENTE_ID, NUMERO_CUENTA, requestNegativo));
        assertEquals("INVALID_AMOUNT", exNeg.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, exNeg.getStatus());

        verify(cuentaRepository, never()).save(any());
    }

    @Test
    void registrarOperacion_cuentaNoPerteneceAlCliente_lanzaForbidden() {
        Long otroClienteId = 99L;
        OperacionCuentaRequest request = new OperacionCuentaRequest(new BigDecimal("10000.00"));
        given(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).willReturn(Optional.of(cuentaActiva));

        NegocioException ex = assertThrows(NegocioException.class,
                () -> cuentaService.registrarCredito(otroClienteId, NUMERO_CUENTA, request));

        assertEquals("FORBIDDEN", ex.getErrorCode());
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(cuentaRepository, never()).save(any());
    }

    @Test
    void registrarOperacion_cuentaBloqueada_lanzaAccountNotActive() {
        cuentaActiva.setEstado(estadoBloqueada);
        OperacionCuentaRequest request = new OperacionCuentaRequest(new BigDecimal("10000.00"));
        given(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).willReturn(Optional.of(cuentaActiva));

        NegocioException exCredito = assertThrows(NegocioException.class,
                () -> cuentaService.registrarCredito(CLIENTE_ID, NUMERO_CUENTA, request));
        assertEquals("ACCOUNT_NOT_ACTIVE", exCredito.getErrorCode());
        assertEquals(HttpStatus.CONFLICT, exCredito.getStatus());

        NegocioException exDebito = assertThrows(NegocioException.class,
                () -> cuentaService.registrarDebito(CLIENTE_ID, NUMERO_CUENTA, request));
        assertEquals("ACCOUNT_NOT_ACTIVE", exDebito.getErrorCode());
        assertEquals(HttpStatus.CONFLICT, exDebito.getStatus());

        assertEquals(new BigDecimal("100000.00"), cuentaActiva.getSaldoDisponible());
        verify(cuentaRepository, never()).save(any());
    }

    @Test
    void registrarOperacion_cuentaNoExiste_lanzaAccountNotFound() {
        OperacionCuentaRequest request = new OperacionCuentaRequest(new BigDecimal("10000.00"));
        given(cuentaRepository.findByNumeroCuenta("0000000000")).willReturn(Optional.empty());

        NegocioException ex = assertThrows(NegocioException.class,
                () -> cuentaService.registrarCredito(CLIENTE_ID, "0000000000", request));

        assertEquals("ACCOUNT_NOT_FOUND", ex.getErrorCode());
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(cuentaRepository, never()).save(any());
    }
}

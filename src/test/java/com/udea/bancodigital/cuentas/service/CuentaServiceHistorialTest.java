package com.udea.bancodigital.cuentas.service;

import com.udea.bancodigital.cuentas.dto.HistorialTransaccionResponse;
import com.udea.bancodigital.cuentas.entity.Cuenta;
import com.udea.bancodigital.cuentas.entity.TipoCuenta;
import com.udea.bancodigital.cuentas.repository.CuentaRepository;
import com.udea.bancodigital.shared.entity.Estado;
import com.udea.bancodigital.shared.entity.TipoEstado;
import com.udea.bancodigital.shared.exception.NegocioException;
import com.udea.bancodigital.transferencias.api.TransaccionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CuentaServiceHistorialTest {

    @Mock
    private CuentaRepository cuentaRepository;

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
    void consultarHistorial_exitoso_mapeaEntradaYSalidaCorrectamente() {
        given(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).willReturn(Optional.of(cuentaActiva));

        // Transacción 1: Salida (transferencia donde cuentaActiva es origen)
        TransaccionApi.TransaccionDetalle t1 = new TransaccionApi.TransaccionDetalle(
                1L, 10L, 20L, "TRANSFERENCIA", new BigDecimal("30000.00"), OffsetDateTime.now(), "Pago"
        );
        // Transacción 2: Entrada (transferencia donde cuentaActiva es destino)
        TransaccionApi.TransaccionDetalle t2 = new TransaccionApi.TransaccionDetalle(
                2L, 20L, 10L, "TRANSFERENCIA", new BigDecimal("50000.00"), OffsetDateTime.now(), "Regalo"
        );
        // Transacción 3: Entrada directa (crédito)
        TransaccionApi.TransaccionDetalle t3 = new TransaccionApi.TransaccionDetalle(
                3L, 10L, null, "CREDITO", new BigDecimal("10000.00"), OffsetDateTime.now(), "Depósito"
        );
        // Transacción 4: Salida directa (débito)
        TransaccionApi.TransaccionDetalle t4 = new TransaccionApi.TransaccionDetalle(
                4L, 10L, null, "DEBITO", new BigDecimal("5000.00"), OffsetDateTime.now(), "Retiro"
        );

        Page<TransaccionApi.TransaccionDetalle> apiPage = new PageImpl<>(List.of(t1, t2, t3, t4));
        given(transaccionApi.consultarHistorial(eq(10L), isNull(), isNull(), isNull(), any(Pageable.class)))
                .willReturn(apiPage);

        Page<HistorialTransaccionResponse> resultado = cuentaService.consultarHistorial(
                CLIENTE_ID, NUMERO_CUENTA, null, null, null, 1, 10
        );

        assertNotNull(resultado);
        assertEquals(4, resultado.getContent().size());

        assertEquals("SALIDA", resultado.getContent().get(0).getTipoMovimiento());
        assertEquals("ENTRADA", resultado.getContent().get(1).getTipoMovimiento());
        assertEquals("ENTRADA", resultado.getContent().get(2).getTipoMovimiento());
        assertEquals("SALIDA", resultado.getContent().get(3).getTipoMovimiento());
    }

    @Test
    void consultarHistorial_rangoFechasInvalido_lanzaInvalidDateRange() {
        given(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).willReturn(Optional.of(cuentaActiva));

        LocalDate desde = LocalDate.of(2026, 10, 20);
        LocalDate hasta = LocalDate.of(2026, 10, 10);

        NegocioException ex = assertThrows(NegocioException.class,
                () -> cuentaService.consultarHistorial(CLIENTE_ID, NUMERO_CUENTA, desde, hasta, null, 1, 10));

        assertEquals("INVALID_DATE_RANGE", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(transaccionApi, never()).consultarHistorial(any(), any(), any(), any(), any());
    }

    @Test
    void consultarHistorial_cuentaNoPerteneceAlCliente_lanzaForbidden() {
        given(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).willReturn(Optional.of(cuentaActiva));

        NegocioException ex = assertThrows(NegocioException.class,
                () -> cuentaService.consultarHistorial(99L, NUMERO_CUENTA, null, null, null, 1, 10));

        assertEquals("FORBIDDEN", ex.getErrorCode());
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(transaccionApi, never()).consultarHistorial(any(), any(), any(), any(), any());
    }

    @Test
    void consultarHistorial_cuentaBloqueada_lanzaAccountNotActive() {
        cuentaActiva.setEstado(estadoBloqueada);
        given(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).willReturn(Optional.of(cuentaActiva));

        NegocioException ex = assertThrows(NegocioException.class,
                () -> cuentaService.consultarHistorial(CLIENTE_ID, NUMERO_CUENTA, null, null, null, 1, 10));

        assertEquals("ACCOUNT_NOT_ACTIVE", ex.getErrorCode());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(transaccionApi, never()).consultarHistorial(any(), any(), any(), any(), any());
    }

    @Test
    void consultarHistorial_cuentaNoExiste_lanzaAccountNotFound() {
        given(cuentaRepository.findByNumeroCuenta("0000000000")).willReturn(Optional.empty());

        NegocioException ex = assertThrows(NegocioException.class,
                () -> cuentaService.consultarHistorial(CLIENTE_ID, "0000000000", null, null, null, 1, 10));

        assertEquals("ACCOUNT_NOT_FOUND", ex.getErrorCode());
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(transaccionApi, never()).consultarHistorial(any(), any(), any(), any(), any());
    }

    @Test
    void consultarHistorial_sinTransacciones_retornaPaginaVacia() {
        given(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).willReturn(Optional.of(cuentaActiva));
        given(transaccionApi.consultarHistorial(eq(10L), any(), any(), any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(Collections.emptyList()));

        Page<HistorialTransaccionResponse> resultado = cuentaService.consultarHistorial(
                CLIENTE_ID, NUMERO_CUENTA, null, null, null, 1, 10
        );

        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
    }
}

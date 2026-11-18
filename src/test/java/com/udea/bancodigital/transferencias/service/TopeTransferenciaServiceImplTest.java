package com.udea.bancodigital.transferencias.service;

import com.udea.bancodigital.shared.exception.CodigosError;
import com.udea.bancodigital.shared.exception.NegocioException;
import com.udea.bancodigital.transferencias.dto.ConfigurarTopesRequest;
import com.udea.bancodigital.transferencias.dto.TopeTransferenciaResponse;
import com.udea.bancodigital.transferencias.entity.TopeTransferencia;
import com.udea.bancodigital.transferencias.repository.TopeTransferenciaRepository;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TopeTransferenciaServiceImplTest {

            private static final Long CLIENTE_ID = 

            @
            private TopeTransferenciaRepository topeTransferenciaReposit

            @InjectM
            private TopeTransferenciaServiceImpl topeTransferenciaServ

            // ==================== HU17: Consultar Topes ================

            @
            @DisplayName("ESCENARIO 1 (HU17): Cliente con topes personalizados retorna dichos valores y esPersonalizado=tr
            void consultarTopes_conTopesPersonalizados_retornaTopesPersonalizados
                        TopeTransferencia topePersonalizado = TopeTransferencia.b
                                       
                                                .clien
                                                .montoMaximoIndividual(new BigDecima
                                                .montoMaximoAcumulado(new BigDecima
                                                .p
                                                .fechaActualizacion(Offse
                                         

                        given(topeTransferenciaRepository.findByClienteId(CLIE
                                                .willReturn(Optional.of(tope

                        TopeTransferenciaResponse response = topeTransferenciaService.consultarTopesParaCliente(CLIE

                        assertThat(response).isNo
                        assertThat(response.getMontoMaximoPorTransferencia()).isEqualByComparingTo("30000
                        assertThat(response.getMontoMaximoAcumuladoPeriodo()).isEqualByComparingTo("80000
                        assertThat(response.getPeriodo()).isEqualTo("D
                        assertThat(response.isEsPersonalizado()).i

                        verify(topeTransferenciaRepository).findByClienteId(CLIE
         

            @
            @DisplayName("ESCENARIO 2 (HU17): Cliente sin topes personalizados retorna topes predeterminados del banco y esPersonalizado=fal
            void consultarTopes_sinTopesPersonalizados_retornaPredeterminadosDelBanco
                        TopeTransferencia topeGlobal = TopeTransferencia.b
                                        
                                                
                                                .montoMaximoIndividual(new BigDecima
                                                .montoMaximoAcumulado(new BigDecimal
                                                .p
                                                .fechaActualizacion(Offse
                                         

                        given(topeTransferenciaRepository.findByClienteId(CLIE
                                                .willReturn(Op
                        given(topeTransferenciaRepository.findByClienteIdI
                                                .willReturn(Optional.

                        TopeTransferenciaResponse response = topeTransferenciaService.consultarTopesParaCliente(CLIE

                        assertThat(response).isNo
                        assertThat(response.getMontoMaximoPorTransferencia()).isEqualByComparingTo("50000
                        assertThat(response.getMontoMaximoAcumuladoPeriodo()).isEqualByComparingTo("150000
                        assertThat(response.getPeriodo()).isEqualTo("D
                        assertThat(response.isEsPersonalizado()).is

                        verify(topeTransferenciaRepository).findByClienteId(CLIE
                        verify(topeTransferenciaRepository).findByClienteIdI
         

            @
            @DisplayName("Sin topes configurados en BD (ni personalizados ni globales) lanza NegocioException SERVICE_UNAVAILAB
            void consultarTopes_sinTopesEnBD_lanzaNegocioException
                        given(topeTransferenciaRepository.findByClienteId(CLIE
                                                .willReturn(Op
                        given(topeTransferenciaRepository.findByClienteIdI
                                                .willReturn(Op

                        assertThatThrownBy(() -> topeTransferenciaService.consultarTopesParaCliente(CLIE
                                                .isInstanceOf(Negocio
                                                .s
                                                            NegocioException ne = (N
                                                            assertThat(ne.getErrorCode()).isEqualTo("TOPE
                                                            assertThat(ne.getStatus()).isEqualTo(HttpStatus.S
                                   
         

            @
            @DisplayName("ESCENARIO 3 (HU17): Sin clienteId (no autenticado) lanza NegocioException UNAUTHORIZ
            void consultarTopes_clienteIdNulo_lanzaNegocioExceptionUnauthorized
                        assertThatThrownBy(() -> topeTransferenciaService.consultarTopesParaClient
                                                .isInstanceOf(Negocio
                                                .s
                                                            NegocioException ne = (N
                                                            assertThat(ne.getErrorCode()).isEqualTo(Codigos
                                                            assertThat(ne.getStatus()).isEqualTo(HttpS
                                   
         

            // ==================== HU18: Configurar Topes ================

            @
            @DisplayName("HU18: Administrador configura valores válidos por primera vez (crea registro globa
            void configurarTopesGlobales_primeraVez_creaYPersisteRegistroGlobal
                        ConfigurarTopesRequest request = ConfigurarTopesRequest.b
                                                .montoMaximoPorTransferencia(new BigDecima
                                                .montoMaximoAcumuladoPeriodo(new BigDecimal
                                         

                        given(topeTransferenciaRepository.findByClienteIdI
                                                .willReturn(Op

                        given(topeTransferenciaRepository.save(any(TopeTransferencia.
                                                .wil
                                                            TopeTransferencia t =
                                                     
                                                 
                                   

                        TopeTransferenciaResponse response = topeTransferenciaService.configurarTopesGlobales(r

                        assertThat(response).isNo
                        assertThat(response.getMontoMaximoPorTransferencia()).isEqualByComparingTo("50000
                        assertThat(response.getMontoMaximoAcumuladoPeriodo()).isEqualByComparingTo("150000
                        assertThat(response.getPeriodo()).isEqualTo("D
                        assertThat(response.isEsPersonalizado()).is

                        ArgumentCaptor<TopeTransferencia> captor = ArgumentCaptor.forClass(TopeTransferencia
                        verify(topeTransferenciaRepository).save(captor.cap
                        TopeTransferencia guardado = captor.get
                        assertThat(guardado.getClienteId()).i
                        assertThat(guardado.getMontoMaximoIndividual()).isEqualByComparingTo("50000
                        assertThat(guardado.getMontoMaximoAcumulado()).isEqualByComparingTo("150000
                        assertThat(guardado.getPeriodo()).isEqualTo("D
                        assertThat(guardado.getFechaActualizacion()).isNo
         

            @
            @DisplayName("HU18: Administrador actualiza valores válidos sobre registro global existente (no duplic
            void configurarTopesGlobales_actualizacion_modificaRegistroExistenteSinDuplicar
                        ConfigurarTopesRequest request = ConfigurarTopesRequest.b
                                                .montoMaximoPorTransferencia(new BigDecima
                                                .montoMaximoAcumuladoPeriodo(new BigDecimal
                                         

                        TopeTransferencia existente = TopeTransferencia.b
                                       
                                                
                                                .montoMaximoIndividual(new BigDecima
                                                .montoMaximoAcumulado(new BigDecimal
                                                .p
                                                .fechaActualizacion(OffsetDateTime.now
                                         

                        given(topeTransferenciaRepository.findByClienteIdI
                                                .willReturn(Optional

                        given(topeTransferenciaRepository.save(any(TopeTransferencia.
                                                .willAnswer(inv -> inv.

                        TopeTransferenciaResponse response = topeTransferenciaService.configurarTopesGlobales(r

                        assertThat(response).isNo
                        assertThat(response.getMontoMaximoPorTransferencia()).isEqualByComparingTo("80000
                        assertThat(response.getMontoMaximoAcumuladoPeriodo()).isEqualByComparingTo("200000
                        assertThat(response.getPeriodo()).isEqualTo("D
                        assertThat(response.isEsPersonalizado()).is

                        verify(topeTransferenciaRepository).save(exi
                        assertThat(existente.getId()).isEqua
                        assertThat(existente.getClienteId()).i
         

            @
            @DisplayName("HU18 Validación: Monto individual igual a cero lanza BAD_REQUE
            void configurarTopesGlobales_montoIndividualCero_lanzaNegocioException
                        ConfigurarTopesRequest request = ConfigurarTopesRequest.b
                                                .montoMaximoPorTransferencia(
                                                .montoMaximoAcumuladoPeriodo(new BigDecimal
                                         

                        assertThatThrownBy(() -> topeTransferenciaService.configurarTopesGlobales(r
                                                .isInstanceOf(Negocio
                                                .s
                                                            NegocioException ne = (N
                                                            assertThat(ne.getErrorCode()).isEqualT
                                                            assertThat(ne.getStatus()).isEqualTo(Http
                                   
         

            @
            @DisplayName("HU18 Validación: Monto individual negativo lanza BAD_REQUE
            void configurarTopesGlobales_montoIndividualNegativo_lanzaNegocioException
                        ConfigurarTopesRequest request = ConfigurarTopesRequest.b
                                                .montoMaximoPorTransferencia(new BigDeci
                                                .montoMaximoAcumuladoPeriodo(new BigDecimal
                                         

                        assertThatThrownBy(() -> topeTransferenciaService.configurarTopesGlobales(r
                                                .isInstanceOf(Negocio
                                                .s
                                                            NegocioException ne = (N
                                                            assertThat(ne.getErrorCode()).isEqualT
                                                            assertThat(ne.getStatus()).isEqualTo(Http
                                   
         

            @
            @DisplayName("HU18 Validación: Límite acumulado igual a cero lanza BAD_REQUE
            void configurarTopesGlobales_montoAcumuladoCero_lanzaNegocioException
                        ConfigurarTopesRequest request = ConfigurarTopesRequest.b
                                                .montoMaximoPorTransferencia(new BigDecima
                                                .montoMaximoAcumuladoPeriodo(
                                         

                        assertThatThrownBy(() -> topeTransferenciaService.configurarTopesGlobales(r
                                                .isInstanceOf(Negocio
                                                .s
                                                            NegocioException ne = (N
                                                            assertThat(ne.getErrorCode()).isEqualT
                                                            assertThat(ne.getStatus()).isEqualTo(Http
                                   
         

            @
            @DisplayName("HU18 Validación: Límite acumulado negativo lanza BAD_REQUE
            void configurarTopesGlobales_montoAcumuladoNegativo_lanzaNegocioException
                        ConfigurarTopesRequest request = ConfigurarTopesRequest.b
                                                .montoMaximoPorTransferencia(new BigDecima
                                                .montoMaximoAcumuladoPeriodo(new BigDec
                                         

                        assertThatThrownBy(() -> topeTransferenciaService.configurarTopesGlobales(r
                                                .isInstanceOf(Negocio
                                                .s
                                                            NegocioException ne = (N
                                                            assertThat(ne.getErrorCode()).isEqualT
                                                            assertThat(ne.getStatus()).isEqualTo(Http
                                   
         

            @
            @DisplayName("HU18 Validación: Límite acumulado inferior al individual lanza BAD_REQUE
            void configurarTopesGlobales_acumuladoMenorAIndividual_lanzaNegocioException
                        ConfigurarTopesRequest request = ConfigurarTopesRequest.b
                                                .montoMaximoPorTransferencia(new BigDecimal
                                                .montoMaximoAcumuladoPeriodo(new BigDecima
                                         

                        assertThatThrownBy(() -> topeTransferenciaService.configurarTopesGlobales(r
                                                .isInstanceOf(Negocio
                                                .s
                                                            NegocioException ne = (N
                                                            assertThat(ne.getErrorCode()).isEqualT
                                                            assertThat(ne.getStatus()).isEqualTo(Http
                                   
         

            @
            @DisplayName("HU18: Consultar topes globales cuando están configurados devuelve 200 con la configuraci
            void consultarTopesGlobales_existente_retornaConfiguracion
                        TopeTransferencia global = TopeTransferencia.b
                                       
                                                
                                                .montoMaximoIndividual(new BigDecima
                                                .montoMaximoAcumulado(new BigDecimal
                                                .p
                                                .fechaActualizacion(Offse
                                         

                        given(topeTransferenciaRepository.findByClienteIdI
                                                .willReturn(Optio

                        TopeTransferenciaResponse response = topeTransferenciaService.consultarTopesGlo

                        assertThat(response).isNo
                        assertThat(response.getMontoMaximoPorTransferencia()).isEqualByComparingTo("50000
                        assertThat(response.getMontoMaximoAcumuladoPeriodo()).isEqualByComparingTo("150000
                        assertThat(response.getPeriodo()).isEqualTo("D
                        assertThat(response.isEsPersonalizado()).is
         

            @
            @DisplayName("HU18: Consultar topes globales cuando no existen lanza SERVICE_UNAVAILAB
            void consultarTopesGlobales_inexistente_lanzaServiceUnavailable
                        given(topeTransferenciaRepository.findByClienteIdI
                                                .willReturn(Op

                        assertThatThrownBy(() -> topeTransferenciaService.consultarTopesGlo
                                                .isInstanceOf(Negocio
                                                .s
                                                            NegocioException ne = (N
                                                            assertThat(ne.getErrorCode()).isEqualTo("TOPE
                                                            assertThat(ne.getStatus()).isEqualTo(HttpStatus.S
                                   
            }
}

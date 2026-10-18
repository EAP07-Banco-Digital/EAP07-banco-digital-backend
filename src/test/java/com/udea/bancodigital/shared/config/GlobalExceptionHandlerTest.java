package com.udea.bancodigital.shared.config;

import com.udea.bancodigital.shared.exception.NegocioException;
import com.udea.bancodigital.usuarios.dto.ActualizarPerfilRequestDTO;
import com.udea.bancodigital.usuarios.dto.MensajesPerfil;
import com.udea.bancodigital.usuarios.dto.MensajesRegistro;
import com.udea.bancodigital.usuarios.dto.RegistroUsuarioRequestDTO;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private static final String TRAZA = "0f1c3a6e-8b2d-4f7a-9c1e-5d3b7a2f4e80";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final MockHttpServletRequest peticion = new MockHttpServletRequest();

    @BeforeEach
    void dejarTrazaEnLaPeticion() {
        peticion.setAttribute(TraceIdFilter.TRACE_ID_ATTR, TRAZA);
    }

    private RegistroUsuarioRequestDTO registroValido() {
        return new RegistroUsuarioRequestDTO(
                "Juan Manuel", "Tabares", "CC", "1017654321", "juan@banco.com",
                LocalDate.of(1998, 4, 15), "3001234567", "Calle 10 #20-30", "Segura123!", "CLIENTE");
    }

    private MethodArgumentNotValidException falloDeValidacion(Object dto, String nombreObjeto) {
        // SpringValidatorAdapter traduce las violaciones de Bean Validation a los
        // FieldError que el handler recibe en una peticion real.
        BeanPropertyBindingResult resultado = new BeanPropertyBindingResult(dto, nombreObjeto);
        new SpringValidatorAdapter(validator).validate(dto, resultado);
        return new MethodArgumentNotValidException(null, resultado);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cuerpo(ResponseEntity<Map<String, Object>> respuesta) {
        return respuesta.getBody();
    }

    // ---------------------------------------------------------- validacion DTO

    @Test
    void unCampoObligatorioAusenteResponde400ConElMensajeDelCriterio() {
        RegistroUsuarioRequestDTO dto = registroValido();
        dto.setNombres("  ");

        ResponseEntity<Map<String, Object>> respuesta = handler.manejarValidacion(falloDeValidacion(dto, "registro"), peticion);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(cuerpo(respuesta).get("errorCode")).isEqualTo("VALIDATION_ERROR");
        assertThat(cuerpo(respuesta).get("message")).isEqualTo(MensajesRegistro.CAMPOS_OBLIGATORIOS);
        assertThat(cuerpo(respuesta).get("traceId")).isEqualTo(TRAZA);
    }

    @Test
    void laRespuestaDeValidacionDetallaTodosLosCamposQueFallaron() {
        RegistroUsuarioRequestDTO dto = registroValido();
        dto.setNombres("  ");
        dto.setApellidos(null);
        dto.setEmail("esto-no-es-un-correo");

        ResponseEntity<Map<String, Object>> respuesta = handler.manejarValidacion(falloDeValidacion(dto, "registro"), peticion);

        @SuppressWarnings("unchecked")
        Map<String, String> detalles = (Map<String, String>) cuerpo(respuesta).get("details");
        assertThat(detalles)
                .containsEntry("nombres", MensajesRegistro.CAMPOS_OBLIGATORIOS)
                .containsEntry("apellidos", MensajesRegistro.CAMPOS_OBLIGATORIOS)
                .containsEntry("email", MensajesRegistro.CORREO_INVALIDO);
    }

    @Test
    void conVariasReglasRotasGanaElMensajeDeLaReglaMasBasica() {
        // Password vacia (NotBlank) y email invalido (Email) a la vez: el
        // criterio espera un solo texto, y NotBlank esta antes en la prioridad.
        RegistroUsuarioRequestDTO dto = registroValido();
        dto.setPassword("");
        dto.setEmail("esto-no-es-un-correo");

        ResponseEntity<Map<String, Object>> respuesta = handler.manejarValidacion(falloDeValidacion(dto, "registro"), peticion);

        assertThat(cuerpo(respuesta).get("message")).isEqualTo(MensajesRegistro.CAMPOS_OBLIGATORIOS);
    }

    @Test
    void unCorreoInvalidoGanaALaContrasenaDebil() {
        // Email esta antes que Pattern en la prioridad: preguntar por el formato
        // del correo tiene sentido, preguntar por la clave todavia no.
        RegistroUsuarioRequestDTO dto = registroValido();
        dto.setEmail("esto-no-es-un-correo");
        dto.setPassword("abcdefgh");

        ResponseEntity<Map<String, Object>> respuesta = handler.manejarValidacion(falloDeValidacion(dto, "registro"), peticion);

        assertThat(cuerpo(respuesta).get("message")).isEqualTo(MensajesRegistro.CORREO_INVALIDO);
    }

    @Test
    void unaFechaDeNacimientoAusenteRespondeConCamposObligatorios() {
        RegistroUsuarioRequestDTO dto = registroValido();
        dto.setFechaNacimiento(null);

        ResponseEntity<Map<String, Object>> respuesta = handler.manejarValidacion(falloDeValidacion(dto, "registro"), peticion);

        assertThat(cuerpo(respuesta).get("message")).isEqualTo(MensajesRegistro.CAMPOS_OBLIGATORIOS);
    }

    @Test
    void unaContrasenaDebilRespondeConElMensajeDeSeguridad() {
        RegistroUsuarioRequestDTO dto = registroValido();
        dto.setPassword("abcdefgh");

        ResponseEntity<Map<String, Object>> respuesta = handler.manejarValidacion(falloDeValidacion(dto, "registro"), peticion);

        assertThat(cuerpo(respuesta).get("message")).isEqualTo(MensajesRegistro.PASSWORD_INSEGURA);
    }

    @Test
    void unaSolicitudDePerfilInvalidaAplicaLosMensajesDelFlujoDePerfil() {
        ActualizarPerfilRequestDTO dto = new ActualizarPerfilRequestDTO("", "correo-malo", "", null);

        ResponseEntity<Map<String, Object>> respuesta = handler.manejarValidacion(falloDeValidacion(dto, "perfil"), peticion);

        assertThat(cuerpo(respuesta).get("message")).isEqualTo(MensajesPerfil.CAMPO_VACIO);
    }

    @Test
    void sinErroresDeCampoRespondeConUnMensajePorDefecto() {
        BeanPropertyBindingResult resultado = new BeanPropertyBindingResult(registroValido(), "registro");
        resultado.addError(new ObjectError("registro", new String[]{"errorGlobal"}, new Object[0], "Regla que no es de campo"));

        ResponseEntity<Map<String, Object>> respuesta =
                handler.manejarValidacion(new MethodArgumentNotValidException(null, resultado), peticion);

        assertThat(cuerpo(respuesta).get("message")).isEqualTo("Los datos enviados no son validos");
    }

    @Test
    void unaReglaFueraDeLaPrioridadNoLeGanaAUnaReglaConocida() {
        // La lista de prioridad es cerrada; una regla que no aparezca en ella se
        // ordena al final, para que el mensaje siga siendo el de una regla basica.
        BeanPropertyBindingResult resultado = new BeanPropertyBindingResult(registroValido(), "registro");
        resultado.addError(new FieldError("registro", "descripcion", "Regla propia del modulo",
                false, new String[]{"Custom"}, null, "Regla propia del modulo"));
        resultado.addError(new FieldError("registro", "nombres", MensajesRegistro.CAMPOS_OBLIGATORIOS,
                false, new String[]{"NotBlank"}, null, MensajesRegistro.CAMPOS_OBLIGATORIOS));

        ResponseEntity<Map<String, Object>> respuesta =
                handler.manejarValidacion(new MethodArgumentNotValidException(null, resultado), peticion);

        assertThat(cuerpo(respuesta).get("message")).isEqualTo(MensajesRegistro.CAMPOS_OBLIGATORIOS);
    }

    // ------------------------------------------------------ reglas de negocio

    @Test
    void unaExcepcionDeNegocioPropagaSuEstadoYCodigo() {
        NegocioException excepcion =
                new NegocioException("DUPLICATE_EMAIL", MensajesPerfil.CORREO_DE_OTRO_USUARIO, HttpStatus.CONFLICT);

        ResponseEntity<Map<String, Object>> respuesta = handler.manejarNegocio(excepcion, peticion);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(cuerpo(respuesta).get("errorCode")).isEqualTo("DUPLICATE_EMAIL");
        assertThat(cuerpo(respuesta).get("message")).isEqualTo(MensajesPerfil.CORREO_DE_OTRO_USUARIO);
        assertThat(cuerpo(respuesta).get("details")).isNull();
        assertThat(cuerpo(respuesta).get("traceId")).isEqualTo(TRAZA);
    }

    @Test
    void credencialesInvalidasResponden401ConCodigoPropio() {
        ResponseEntity<Map<String, Object>> respuesta =
                handler.manejarCredencialesInvalidas(new BadCredentialsException("Credenciales invalidas"), peticion);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(cuerpo(respuesta).get("errorCode")).isEqualTo("INVALID_CREDENTIALS");
        assertThat(cuerpo(respuesta).get("message")).isEqualTo("Credenciales invalidas");
    }

    @Test
    void unaExcepcionNoContempladaResponde500SinFiltrarDetallesInternos() {
        ResponseEntity<Map<String, Object>> respuesta =
                handler.manejarErrorGeneral(new IllegalStateException("detalle interno que no debe filtrarse"), peticion);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(cuerpo(respuesta).get("errorCode")).isEqualTo("INTERNAL_ERROR");
        assertThat(cuerpo(respuesta).get("message")).isEqualTo("Ocurrio un error inesperado");
        assertThat(respuesta.getBody().values()).doesNotContain("detalle interno que no debe filtrarse");
    }

    @Test
    void todaRespuestaDeErrorLlevaLasCuatroClavesDelFormatoComun() {
        ResponseEntity<Map<String, Object>> respuesta =
                handler.manejarNegocio(new NegocioException("X", "mensaje", HttpStatus.BAD_REQUEST), peticion);

        assertThat(cuerpo(respuesta)).containsOnlyKeys("errorCode", "message", "details", "traceId");
    }
}

package com.udea.bancodigital.shared.config;

import com.udea.bancodigital.shared.exception.NegocioException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // De mas basica a mas especifica: no tiene sentido reclamar el formato de
    // un correo que el usuario nunca escribio.
    private static final List<String> PRIORIDAD_VALIDACION =
            List.of("NotBlank", "NotNull", "NotEmpty", "Email", "Pattern");

    // Metodo de apoyo: arma la respuesta con la misma forma siempre,
    // para no repetir la construccion del mapa en cada handler.
    private ResponseEntity<Map<String, Object>> construirRespuesta(
            HttpStatus status, String errorCode, String message,
            Object details, HttpServletRequest request
    ) {
        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("errorCode", errorCode);
        respuesta.put("message", message);
        respuesta.put("details", details);
        respuesta.put("traceId", request.getAttribute(TraceIdFilter.TRACE_ID_ATTR));

        return ResponseEntity.status(status).body(respuesta);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> manejarValidacion(
            MethodArgumentNotValidException exception, HttpServletRequest request) {

        List<FieldError> errores = exception.getBindingResult().getFieldErrors();

        Map<String, String> detalles = new HashMap<>();
        errores.forEach(error -> detalles.put(error.getField(), error.getDefaultMessage()));

        // Una sola peticion puede romper varias reglas a la vez, pero el criterio
        // de aceptacion espera un unico texto: gana la regla mas basica que fallo.
        String mensaje = errores.stream()
                .min(Comparator.comparingInt((FieldError error) -> {
                    int posicion = PRIORIDAD_VALIDACION.indexOf(error.getCode());
                    return posicion < 0 ? PRIORIDAD_VALIDACION.size() : posicion;
                }))
                .map(FieldError::getDefaultMessage)
                .orElse("Los datos enviados no son validos");

        return construirRespuesta(
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", mensaje, detalles, request
        );
    }

    // Un id de la URL que no es numerico (ej. PUT /api/usuarios/abc/rol) es un
    // error del cliente, no un 500 del catch-all.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> manejarParametroInvalido(
            MethodArgumentTypeMismatchException exception, HttpServletRequest request) {

        return construirRespuesta(
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "El parametro '" + exception.getName() + "' no tiene un formato valido",
                null, request
        );
    }

    // JSON mal formado, o un campo con un tipo que no se puede convertir (por
    // ejemplo, una fecha "2020-13-45" o un monto "mil"): es un error del cliente.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> manejarCuerpoIlegible(
            HttpMessageNotReadableException exception, HttpServletRequest request) {

        return construirRespuesta(
                HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "El cuerpo de la peticion no es un JSON valido o algun campo tiene un formato invalido",
                null, request
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> manejarRutaInexistente(
            NoResourceFoundException exception, HttpServletRequest request) {

        return construirRespuesta(
                HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                "El recurso solicitado no existe", null, request
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> manejarMetodoNoPermitido(
            HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {

        return construirRespuesta(
                HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "El metodo " + exception.getMethod() + " no esta permitido en este recurso",
                null, request
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> manejarTipoDeContenidoNoSoportado(
            HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {

        return construirRespuesta(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "El cuerpo de la peticion debe enviarse como application/json", null, request
        );
    }

    // Ultima defensa de las restricciones de la base (correo o documento unico):
    // si dos peticiones pasan a la vez la validacion del servicio, la segunda
    // choca aqui. El detalle de la base no se devuelve: delata el esquema.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> manejarConflictoDeDatos(
            DataIntegrityViolationException exception, HttpServletRequest request) {

        return construirRespuesta(
                HttpStatus.CONFLICT, "DATA_CONFLICT",
                "La operacion entra en conflicto con datos ya registrados", null, request
        );
    }

    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<Map<String, Object>> manejarNegocio(
            NegocioException exception, HttpServletRequest request) {

        return construirRespuesta(
                exception.getStatus(), exception.getErrorCode(),
                exception.getMessage(), null, request
        );
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> manejarCredencialesInvalidas(
            BadCredentialsException exception, HttpServletRequest request) {

        return construirRespuesta(
                HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                exception.getMessage(), null, request
        );
    }

    // Red de seguridad: cualquier excepcion no contemplada explicitamente
    // tambien responde en el mismo formato uniforme, nunca con un stacktrace crudo.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> manejarErrorGeneral(
            Exception exception, HttpServletRequest request) {

        return construirRespuesta(
                HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Ocurrio un error inesperado", null, request
        );
    }
}

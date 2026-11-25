# ADR-05: Contrato uniforme de errores con `errorCode` y `traceId`

| Campo | Valor |
|---|---|
| Estado | Aceptado |
| Fecha | 2026-10-12 (DEF-HU5-01 y DEF-T-02); ampliado el 2026-10-18 (errores 4xx) |
| Prioridad | 5 · Media-baja: mejora la integración con el frontend y el soporte, sin cambiar la arquitectura |
| HU relacionadas | HU1, HU2, HU4, HU5, HU6, HU7 |

## Contexto

En el Sprint 1 cada error salía con un formato distinto: algunos con cuerpo JSON, el 403 sin cuerpo,
el 401 con `UNAUTHENTICATED` en un lugar y `UNAUTHORIZED` en otro, y errores del cliente (JSON mal
formado, método no permitido) que respondían 500. El frontend no podía distinguir los casos y no había
forma de relacionar un error reportado con el log del servidor.

## Decisión

Todo error responde con el mismo cuerpo:

```json
{ "errorCode": "ACCOUNT_TYPE_ALREADY_EXISTS", "message": "…", "details": null, "traceId": "…" }
```

- `errorCode` es estable y lo usa el cliente para decidir; `message` es el texto para mostrar.
- Las reglas de negocio lanzan `NegocioException(errorCode, mensaje, estado HTTP)` y
  `GlobalExceptionHandler` las traduce; las validaciones de Bean Validation salen como 400
  `VALIDATION_ERROR` con el detalle por campo.
- `CustomAuthEntryPoint` (401 `UNAUTHORIZED`) y `CustomAccessDeniedHandler` (403 `FORBIDDEN`) usan el
  mismo formato.
- `TraceIdFilter` asigna un `traceId` a cada petición; el 500 lo registra en el log junto con la traza.

## Alternativas consideradas

- **RFC 7807 (`application/problem+json`)**: estándar, pero los `errorCode` ya estaban en uso en las
  pruebas y en las colecciones de Postman; se puede adoptar después manteniendo `errorCode` como
  extensión.
- **Respuestas por defecto de Spring**: cambian entre versiones y exponen detalles internos.

## Consecuencias

- Positivas: el cliente maneja todos los errores con un solo parser; los códigos de error forman parte
  del contrato y se documentan por endpoint; el `traceId` permite ubicar cualquier error en los logs.
- Negativas: cada nuevo caso de error requiere definir y documentar su `errorCode`; las constantes deben
  mantenerse sincronizadas con el frontend.

## Cómo se verifica

`GlobalExceptionHandlerTest`, `CustomAuthEntryPointTest`, `CustomAccessDeniedHandlerTest`. Contra el
despliegue, un correo repetido en el registro responde 409 `EMAIL_ALREADY_REGISTERED` con `traceId`.

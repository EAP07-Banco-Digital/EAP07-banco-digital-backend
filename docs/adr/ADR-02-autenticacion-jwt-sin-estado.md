# ADR-02: Autenticación sin estado con JWT y rol vigente leído de la base

| Campo | Valor |
|---|---|
| Estado | Aceptado |
| Fecha | 2026-09-19 (HU2/HU3, commit `317b5e4`); ajustado el 2026-10-18 (el token identifica al usuario por id) |
| Prioridad | 2 · Alta: protege todos los endpoints de cliente y es un requisito de seguridad del banco |
| HU relacionadas | HU2 (login), HU4 y HU7 (perfil), HU5 y HU6 (cuentas); después HU8 y HU9 |

## Contexto

Salvo el registro (HU1) y el inicio de sesión (HU2), todos los endpoints exigen saber quién es el cliente.
La API se consume desde Swagger UI, Postman y, más adelante, un frontend separado; corre en un solo
contenedor que Render puede reiniciar en cualquier momento, así que no conviene guardar sesiones en
memoria. Además, un cliente nunca debe poder consultar el perfil o el saldo de otro.

## Decisión

- El login (HU2) valida la contraseña con **BCrypt** y emite un **JWT firmado con HS256** que expira en
  1 hora (`jwt.expiration-ms`). El secreto llega por la variable de entorno `JWT_SECRET`.
- El *subject* del token es el **id del usuario**, no su correo: así cambiar el correo en HU7 no invalida
  la sesión abierta.
- En cada petición, `JwtAuthenticationFilter` valida firma y expiración y lee de la base, a través de
  `UsuarioApi.obtenerIdentidadPorId`, el correo, el rol y el estado vigentes. Un usuario inactivo o
  inexistente queda sin autenticar.
- `SecurityConfig` deja públicos solo el registro, el login y Swagger; el resto exige el rol `CLIENTE`
  (y las rutas administrativas, `ADMIN`). La identidad siempre se toma del token, nunca de un id en la URL
  (`/api/usuarios/me`).

## Alternativas consideradas

- **Sesión HTTP con cookie**: obliga a guardar estado en el servidor o a usar una sesión compartida;
  se pierde con cada reinicio del contenedor.
- **Rol dentro del token**: evita una consulta por petición, pero un cambio de rol o una desactivación
  no aplicaría hasta que el token expire.
- **Proveedor externo (OAuth2/Keycloak)**: más robusto, pero agrega infraestructura que el alcance del
  curso no justifica.

## Consecuencias

- Positivas: el servidor no guarda sesiones y puede reiniciarse o escalar sin perderlas; los cambios de
  rol y de estado aplican en la siguiente petición; las contraseñas nunca se guardan en claro.
- Negativas: una consulta a la base por petición autenticada; un token robado sirve hasta que expira
  (se mitigó en HU8 con una lista negra de tokens revocados); con HS256, quien conozca `JWT_SECRET` puede
  firmar tokens, así que el secreto debe rotarse si se filtra.

## Cómo se verifica

Pruebas `JwtServiceTest`, `JwtAuthenticationFilterTest`, `AuthServiceTest` y
`CuentaControllerSeguridadTest`. Prueba contra el despliegue: sin token, `GET /api/usuarios/me` responde
401 `UNAUTHORIZED`.

# ADR-03: PostgreSQL administrado en Neon, con credenciales por variables de entorno

| Campo | Valor |
|---|---|
| Estado | Aceptado |
| Fecha | 2026-09-13 (PR #1, conexión a Neon con variables de entorno) |
| Prioridad | 3 · Media-alta: todas las HU persisten datos y la base es compartida por el equipo |
| HU relacionadas | HU1, HU2, HU4, HU5, HU6, HU7 |

## Contexto

Un banco necesita transacciones ACID, restricciones de unicidad (correo, documento, número de cuenta) y
bloqueo de filas al mover dinero. El equipo no tiene un servidor propio y necesita una base accesible
desde los equipos de desarrollo y desde Render, sin costo durante el semestre.

## Decisión

Usar **PostgreSQL 18 administrado por Neon** (región AWS us-east-2), conectado mediante su *pooler* de
conexiones (PgBouncer) con **SSL obligatorio**. La aplicación accede con Spring Data JPA (Hibernate).

Las credenciales nunca se guardan en el repositorio: `application.properties` solo referencia
`DB_URL`, `DB_USERNAME` y `DB_PASSWORD`, que en local se leen de un `.env` (ignorado por git, con
`.env.example` como plantilla) y en Render se configuran como variables del servicio.

## Alternativas consideradas

- **MySQL** (su driver venía en la plantilla inicial y se retiró después): también relacional, pero
  PostgreSQL ofrece índices parciales, que luego se usaron para garantizar "una sola cuenta ACTIVA por
  tipo" (DEF-HU5-02).
- **PostgreSQL de Render**: deja base y servicio en el mismo proveedor, pero la base gratuita de Render
  expira a los 30 días.
- **Base embebida (H2) o local por desarrollador**: no reproduce el comportamiento real de PostgreSQL
  y cada persona vería datos distintos.

## Consecuencias

- Positivas: transacciones y bloqueos reales; nada de credenciales en el repositorio; la misma base
  sirve para desarrollo, pruebas manuales y el despliegue.
- Negativas: la base de pruebas es **compartida**, así que los datos de prueba deben marcarse
  (`qa.*@test.local`); Neon suspende la base inactiva y la primera conexión tarda unos segundos;
  `ddl-auto=update` modifica el esquema al arrancar, por lo que el esquema no está versionado con
  migraciones (pendiente: Flyway).

## Cómo se verifica

`application.properties` no contiene credenciales; `.env` está en `.gitignore`. Las pruebas de
concurrencia (`AperturaCuentaConcurrenteTest`) corren contra PostgreSQL en Docker con Testcontainers.

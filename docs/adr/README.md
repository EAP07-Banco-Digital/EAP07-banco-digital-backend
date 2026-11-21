# Architecture Decision Records (ADR)

Decisiones de arquitectura del backend del Banco Digital, ordenadas por prioridad. La prioridad combina
cuánto código afecta la decisión y cuánto costaría revertirla.

| Prioridad | ADR | Decisión | Estado |
|---|---|---|---|
| 1 · Alta | [ADR-01](ADR-01-monolito-modular.md) | Monolito modular con APIs públicas entre módulos | Aceptado |
| 2 · Alta | [ADR-02](ADR-02-autenticacion-jwt-sin-estado.md) | Autenticación sin estado con JWT y rol vigente leído de la base | Aceptado |
| 3 · Media-alta | [ADR-03](ADR-03-postgresql-en-neon.md) | PostgreSQL administrado en Neon, con credenciales por variables de entorno | Aceptado |
| 4 · Media | [ADR-04](ADR-04-despliegue-docker-render.md) | Despliegue en Render con una imagen Docker multietapa | Aceptado |
| 5 · Media-baja | [ADR-05](ADR-05-contrato-uniforme-de-errores.md) | Contrato uniforme de errores con `errorCode` y `traceId` | Aceptado |

Cada ADR sigue la misma estructura: contexto, decisión, alternativas consideradas, consecuencias y cómo
se verifica. Una decisión que cambie se registra en un ADR nuevo que reemplaza al anterior (estado
"Reemplazado por ADR-xx"), sin borrar el original.

# ADR-01: Monolito modular con APIs públicas entre módulos

| Campo | Valor |
|---|---|
| Estado | Aceptado |
| Fecha | 2026-09-22 (commit `7821eb8`, migración a monolito modular) |
| Prioridad | 1 · Alta: define la estructura de todo el código y es la decisión más costosa de revertir |
| HU relacionadas | HU1, HU2, HU4, HU5, HU6, HU7 (y todas las posteriores) |

## Contexto

El backend del Banco Digital lo construyen cuatro personas en paralelo, cada una en una rama por historia
de usuario. La primera versión (commit `317b5e4`, HU1, HU4 y HU7) estaba organizada por capas planas (`controller/`,
`service/`, `repository/`, `entity/`, `DTO/` en la raíz), así que las HU de usuarios y de cuentas tocaban los
mismos paquetes: más conflictos al fusionar y nada que impidiera que un módulo usara las clases internas
de otro.

El equipo no tiene todavía la necesidad ni la capacidad operativa de mantener varios servicios desplegados
por separado: un único servicio en Render y una sola base de datos son suficientes para el volumen del curso.

## Decisión

Organizar la aplicación como un **monolito modular**: un paquete por módulo de negocio bajo
`com.udea.bancodigital` (`usuarios`, `cuentas`, y más tarde `transferencias` y `reportes`) más un módulo
transversal `shared`. Dentro de cada módulo se mantienen las capas (`controller`, `service`, `repository`,
`entity`, `dto`, `mapper`, `api`).

Reglas:

1. Un módulo solo puede usar de otro su interfaz pública en `api/` (`UsuarioApi`, `CuentaApi`), nunca su
   `Service` ni su `Repository`.
2. Las entidades JPA no cruzan el borde del módulo: entre módulos viajan *records* (`CuentaInfo`,
   `Identidad`) y las referencias se guardan como identificadores (`Cuenta.clienteId` es un `Long`).
3. Lo transversal (seguridad, JWT, manejo de errores, catálogo de estados) vive en `shared`.

## Alternativas consideradas

- **Capas planas** (la estructura inicial): simple al comenzar, pero no separa responsabilidades por
  dominio y concentra los conflictos de fusión en los mismos archivos.
- **Microservicios** (un servicio por módulo): aislamiento fuerte, pero exige varios despliegues,
  comunicación por red, transacciones distribuidas para mover dinero y más infraestructura de la que el
  equipo puede operar en el semestre.
- **Arquitectura hexagonal estricta** (puertos y adaptadores en cada módulo): mayor desacoplamiento del
  framework, a costa de duplicar modelos y *mappers* en un dominio todavía pequeño.

## Consecuencias

- Positivas: cada HU vive en su módulo y las ramas chocan menos; las dependencias entre módulos son
  explícitas y revisables en las interfaces `api/`; un solo despliegue y una sola transacción de base de
  datos (necesaria para bloquear cuentas al mover dinero); el camino a microservicios queda abierto,
  porque las interfaces `api/` ya marcan los bordes.
- Negativas: las reglas de dependencia no las impone el compilador, sino la revisión de PR; todos los
  módulos comparten la misma base de datos y escalan juntos; un ciclo de dependencias entre módulos
  impide arrancar la aplicación (ocurrió con `CuentaService` ↔ `TransaccionApi` y se resolvió separando
  `CuentaApiImpl`).

## Cómo se verifica

Revisión de cada PR: ninguna clase fuera de `usuarios` importa `usuarios.service` ni
`usuarios.repository` (y lo mismo para `cuentas`). Diagramas de paquetes y de componentes en
[`docs/arquitectura`](../arquitectura/ARQUITECTURA.md).

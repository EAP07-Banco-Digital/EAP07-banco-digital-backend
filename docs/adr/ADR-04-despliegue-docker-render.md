# ADR-04: Despliegue en Render con una imagen Docker multietapa

| Campo | Valor |
|---|---|
| Estado | Aceptado |
| Fecha | 2026-09-19 (commits `7e7b735`, puerto dinámico, y `df7a407`, Dockerfile para Render) |
| Prioridad | 4 · Media: necesaria para la entrega y la demo, y fácil de cambiar de proveedor |
| HU relacionadas | Todas: es el entorno donde se demuestran |

## Contexto

La asignatura pide un despliegue inicial accesible en la nube para la demo de cada sprint. El equipo
necesita un entorno gratuito, con HTTPS y que se pueda construir desde el repositorio sin depender de
la máquina de nadie.

## Decisión

Empaquetar la aplicación con el `Dockerfile` del repositorio, en dos etapas:

1. **Build**: `maven:3.9.6-eclipse-temurin-21` descarga dependencias y ejecuta
   `mvn clean package -DskipTests` (las pruebas ya corren en GitHub Actions).
2. **Run**: `eclipse-temurin:21-jre-alpine` copia solo el `.jar` y lo ejecuta con `java -jar app.jar`.

La imagen se publica como *Web Service* en **Render**, que la construye desde GitHub, sirve HTTPS y
asigna el puerto en la variable `PORT` (`server.port=${PORT:8080}`). Los secretos se configuran como
variables del servicio.

URL: https://eap07-banco-digital-backend.onrender.com (Swagger UI en `/swagger-ui/index.html`).

## Alternativas consideradas

- **Otras plataformas PaaS (Railway, Fly.io)**: ofrecen algo equivalente; se eligió Render porque su
  plan gratuito ya cubría HTTPS y la construcción desde el Dockerfile.
- **Nube pública (Azure App Service, AWS)**: más control, pero más configuración y riesgo de costos para
  un proyecto académico.
- **Jar sin contenedor**: Render lo permite, pero ata la versión de Java al entorno del proveedor;
  con Docker, la imagen es la misma en local y en la nube.

## Consecuencias

- Positivas: build reproducible e igual en cualquier máquina; imagen final pequeña (solo JRE y el
  `.jar`); HTTPS sin configurar certificados; cambiar de proveedor solo requiere la misma imagen.
- Negativas: en el plan gratuito el servicio se suspende tras un rato sin tráfico y la primera petición
  tarda unos 20 segundos; el despliegue no está atado al pipeline de calidad, así que la versión
  publicada puede quedar detrás de `main` hasta que se vuelva a desplegar.

## Cómo se verifica

`GET /v3/api-docs` del despliegue responde 200 con la especificación OpenAPI, y el flujo de HU1 a HU7
se probó contra la URL pública (ver el entregable del Sprint 1).

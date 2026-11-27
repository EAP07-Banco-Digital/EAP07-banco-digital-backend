# EAP07-banco-digital-backend [![Quality gate status](https://sonarcloud.io/api/project_badges/measure?project=EAP07-Banco-Digital_EAP07-banco-digital-backend&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=EAP07-Banco-Digital_EAP07-banco-digital-backend)
Fábrica escuela 2026-2

## Cómo levantar el proyecto localmente

1. Clona el repositorio.
2. Copia `.env.example` como `.env`.
3. Pide las credenciales de Neon al equipo; no están en el repositorio.
4. Completa `.env` con esos valores.
5. Ejecuta `./mvnw spring-boot:run`.

## Entorno completo con Docker Compose (API + PostgreSQL + Prometheus + Grafana)

No necesita las credenciales de Neon: levanta un PostgreSQL propio y siembra los catálogos.

```bash
docker compose up -d --build
```

| Servicio   | URL                                   | Notas                                         |
|------------|---------------------------------------|-----------------------------------------------|
| API        | http://localhost:8080/swagger-ui.html | Puerto de gestión 8081 solo en la red interna |
| Prometheus | http://localhost:9090                 | Raspa `api:8081/actuator/prometheus` cada 15 s |
| Grafana    | http://localhost:3000                 | `admin` / `admin`; tablero «Banco Digital · API» |

Si algún puerto ya está ocupado: `API_PORT=18080 PROMETHEUS_PORT=19090 GRAFANA_PORT=13000 docker compose up -d`.
Las reglas de alerta están en `observabilidad/prometheus/alertas.yml`. Para apagar: `docker compose down`
(con `-v` se borran también los datos).

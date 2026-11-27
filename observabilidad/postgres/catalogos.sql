-- Catalogos minimos para el entorno de docker compose. Mismos nombres que la
-- base del equipo (Neon). Es idempotente: Spring lo ejecuta en cada arranque.
INSERT INTO roles (nombre) SELECT r FROM (VALUES ('CLIENTE'), ('ADMIN')) AS v(r)
    WHERE NOT EXISTS (SELECT 1 FROM roles WHERE nombre = v.r);
INSERT INTO tipo_estado (nombre) SELECT t FROM (VALUES ('USUARIO'), ('CUENTA')) AS v(t)
    WHERE NOT EXISTS (SELECT 1 FROM tipo_estado WHERE nombre = v.t);
INSERT INTO estados (tipo_estado_id, nombre)
    SELECT te.id, 'ACTIVO' FROM tipo_estado te WHERE te.nombre = 'USUARIO'
    AND NOT EXISTS (SELECT 1 FROM estados e WHERE e.nombre = 'ACTIVO');
INSERT INTO estados (tipo_estado_id, nombre)
    SELECT te.id, v.n FROM tipo_estado te, (VALUES ('ACTIVA'), ('BLOQUEADA'), ('INACTIVA')) AS v(n)
    WHERE te.nombre = 'CUENTA' AND NOT EXISTS (SELECT 1 FROM estados e WHERE e.nombre = v.n);
INSERT INTO tipo_cuenta (nombre) SELECT t FROM (VALUES ('AHORROS'), ('CORRIENTE')) AS v(t)
    WHERE NOT EXISTS (SELECT 1 FROM tipo_cuenta WHERE nombre = v.t);
INSERT INTO tipo_transaccion (nombre) SELECT t FROM (VALUES ('TRANSFERENCIA'), ('CREDITO'), ('DEBITO')) AS v(t)
    WHERE NOT EXISTS (SELECT 1 FROM tipo_transaccion WHERE nombre = v.t);

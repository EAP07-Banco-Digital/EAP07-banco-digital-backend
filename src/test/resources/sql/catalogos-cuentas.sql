-- Catalogos minimos para las pruebas que levantan un PostgreSQL en Docker.
-- Replican los nombres que tiene la base del equipo (Neon).
INSERT INTO tipo_estado (nombre) VALUES ('USUARIO'), ('CUENTA');
INSERT INTO estados (tipo_estado_id, nombre)
    SELECT id, 'ACTIVO' FROM tipo_estado WHERE nombre = 'USUARIO';
INSERT INTO estados (tipo_estado_id, nombre)
    SELECT id, n FROM tipo_estado, (VALUES ('ACTIVA'), ('BLOQUEADA'), ('INACTIVA')) AS e(n)
    WHERE tipo_estado.nombre = 'CUENTA';
INSERT INTO tipo_cuenta (nombre) VALUES ('AHORROS'), ('CORRIENTE');
INSERT INTO tipo_transaccion (nombre) VALUES ('TRANSFERENCIA'), ('CREDITO'), ('DEBITO');

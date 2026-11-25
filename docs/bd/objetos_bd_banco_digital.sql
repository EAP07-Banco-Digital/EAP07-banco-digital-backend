-- Banco Digital (EAP07) - objetos programables del esquema public
-- Extraido de Neon (neondb) el 2026-11-24 con pg_get_functiondef, pg_get_viewdef y pg_get_triggerdef
-- Orden de ejecucion: funciones -> vistas -> triggers

-- =====================================================
-- 1. FUNCIONES
-- =====================================================

-- existe_cliente
CREATE OR REPLACE FUNCTION public.existe_cliente(p_tipo_documento character varying, p_numero_documento character varying)
 RETURNS character varying
 LANGUAGE plpgsql
AS $function$

DECLARE
  nombre_cliente VARCHAR(600);
  resultado VARCHAR(100);

BEGIN
  nombre_cliente := (SELECT nombres || ' ' || apellidos
                     FROM clientes
                     WHERE tipo_documento = p_tipo_documento
                       AND numero_documento = p_numero_documento);

  IF nombre_cliente IS NULL THEN
    RAISE NOTICE 'el cliente no existe';
    resultado := 'No existe';
  ELSE
    RAISE NOTICE 'el cliente es %', nombre_cliente;
    resultado := 'Existe: ' || nombre_cliente;
  END IF;

  RETURN resultado;
END;

$function$;

-- registrar_cliente
CREATE OR REPLACE FUNCTION public.registrar_cliente(p_nombres character varying, p_apellidos character varying, p_tipo_documento character varying, p_numero_documento character varying, p_email character varying, p_telefono character varying, p_direccion character varying)
 RETURNS character varying
 LANGUAGE plpgsql
AS $function$

DECLARE
  nombre_existente VARCHAR(255);
  resultado VARCHAR(50);

BEGIN
  nombre_existente := (SELECT nombres
                       FROM clientes
                       WHERE tipo_documento = p_tipo_documento
                         AND numero_documento = p_numero_documento);

  IF nombre_existente IS NULL THEN
    INSERT INTO clientes (nombres, apellidos, tipo_documento, numero_documento,
                          email, telefono, direccion, fecha_registro)
    VALUES (p_nombres, p_apellidos, p_tipo_documento, p_numero_documento,
            LOWER(p_email), p_telefono, p_direccion, NOW());

    resultado := 'Cliente creado';
  ELSE
    UPDATE clientes
    SET nombres = p_nombres,
        apellidos = p_apellidos,
        email = LOWER(p_email),
        telefono = p_telefono,
        direccion = p_direccion
    WHERE tipo_documento = p_tipo_documento
      AND numero_documento = p_numero_documento;

    resultado := 'Cliente actualizado';
  END IF;

  RETURN resultado;
END;

$function$;

-- saldo_total_cliente
CREATE OR REPLACE FUNCTION public.saldo_total_cliente(p_cliente_id bigint)
 RETURNS numeric
 LANGUAGE plpgsql
AS $function$

DECLARE
  total NUMERIC(15,2);

BEGIN
  total := (SELECT SUM(C.saldo_disponible)
            FROM cuentas AS C
            INNER JOIN estados AS E
              ON E.id = C.estado_id
            WHERE C.cliente_id = p_cliente_id
              AND E.nombre = 'ACTIVA');

  IF total IS NULL THEN
    total := 0;
  END IF;

  RETURN total;
END;

$function$;

-- usuario_bloqueado
CREATE OR REPLACE FUNCTION public.usuario_bloqueado(p_email character varying)
 RETURNS character varying
 LANGUAGE plpgsql
AS $function$

DECLARE
  existe INT;
  fecha_bloqueo TIMESTAMP WITH TIME ZONE;
  resultado VARCHAR(50);

BEGIN
  existe := (SELECT COUNT(1)
             FROM usuarios
             WHERE email = LOWER(p_email));

  IF existe = 0 THEN
    resultado := 'Usuario no existe';
  ELSE
    fecha_bloqueo := (SELECT bloqueado_hasta
                      FROM usuarios
                      WHERE email = LOWER(p_email));

    IF fecha_bloqueo IS NOT NULL AND fecha_bloqueo > NOW() THEN
      resultado := 'Bloqueado';
    ELSE
      resultado := 'Activo';
    END IF;
  END IF;

  RETURN resultado;
END;

$function$;

-- fn_bloquear_usuario
CREATE OR REPLACE FUNCTION public.fn_bloquear_usuario()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$

BEGIN
  -- Solo cuando se suma un nuevo intento fallido y se llega a 5 o más
  IF NEW.intentos_fallidos >= 5 AND COALESCE(OLD.intentos_fallidos, 0) < NEW.intentos_fallidos THEN
    NEW.bloqueado_hasta := NOW() + INTERVAL '30 minutes';
    RAISE NOTICE 'Usuario bloqueado hasta %', NEW.bloqueado_hasta;
  END IF;

  RETURN NEW;
END;

$function$;

-- fn_eliminar_cliente
CREATE OR REPLACE FUNCTION public.fn_eliminar_cliente()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$

DECLARE
  intcantidad_cuentas INT;

BEGIN
  intcantidad_cuentas := (SELECT COUNT(1)
                          FROM cuentas
                          WHERE cliente_id = OLD.id);

  IF intcantidad_cuentas > 0 THEN
    RAISE EXCEPTION 'No puede borrar el cliente porque tiene cuentas asociadas';
  END IF;

  RETURN OLD;
END;

$function$;

-- fn_validar_cuenta_activa_por_tipo
CREATE OR REPLACE FUNCTION public.fn_validar_cuenta_activa_por_tipo()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$

DECLARE
  nombre_estado VARCHAR(255);
  intcantidad_cuentas INT;

BEGIN
  nombre_estado := (SELECT nombre
                    FROM estados
                    WHERE id = NEW.estado_id);

  IF nombre_estado = 'ACTIVA' THEN

    intcantidad_cuentas := (SELECT COUNT(1)
                            FROM cuentas AS C
                            INNER JOIN estados AS E
                              ON E.id = C.estado_id
                            WHERE C.cliente_id = NEW.cliente_id
                              AND C.tipo_cuenta_id = NEW.tipo_cuenta_id
                              AND E.nombre = 'ACTIVA');

    IF intcantidad_cuentas > 0 THEN
      RAISE EXCEPTION 'El cliente ya tiene una cuenta activa de este tipo';
    END IF;

  END IF;

  RETURN NEW;
END;

$function$;

-- fn_validar_cuentas_activas
CREATE OR REPLACE FUNCTION public.fn_validar_cuentas_activas()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$

DECLARE
  nombre_tipo VARCHAR(50);
  estado_origen VARCHAR(255);
  estado_destino VARCHAR(255);

BEGIN
  nombre_tipo := (SELECT nombre
                  FROM tipo_transaccion
                  WHERE id = NEW.tipo_transaccion_id);

  IF nombre_tipo = 'TRANSFERENCIA' OR nombre_tipo = 'DEBITO' THEN

    estado_origen := (SELECT E.nombre
                      FROM cuentas AS C
                      INNER JOIN estados AS E
                        ON E.id = C.estado_id
                      WHERE C.id = NEW.cuenta_origen_id);

    IF estado_origen <> 'ACTIVA' THEN
      RAISE EXCEPTION 'La cuenta de origen no está activa (estado: %)', estado_origen;
    END IF;

  END IF;

  IF nombre_tipo = 'TRANSFERENCIA' THEN

    estado_destino := (SELECT E.nombre
                       FROM cuentas AS C
                       INNER JOIN estados AS E
                         ON E.id = C.estado_id
                       WHERE C.id = NEW.cuenta_destino_id);

    IF estado_destino <> 'ACTIVA' THEN
      RAISE EXCEPTION 'La cuenta de destino no está activa (estado: %)', estado_destino;
    END IF;

  END IF;

  RETURN NEW;
END;

$function$;

-- fn_validar_tope_transferencia
CREATE OR REPLACE FUNCTION public.fn_validar_tope_transferencia()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$

DECLARE
  nombre_tipo VARCHAR(50);
  id_cliente BIGINT;
  tope_individual NUMERIC(15,2);

BEGIN
  nombre_tipo := (SELECT nombre
                  FROM tipo_transaccion
                  WHERE id = NEW.tipo_transaccion_id);

  IF nombre_tipo = 'TRANSFERENCIA' THEN

    id_cliente := (SELECT cliente_id
                   FROM cuentas
                   WHERE id = NEW.cuenta_origen_id);

    -- Tope personalizado del cliente
    tope_individual := (SELECT MIN(monto_maximo_individual)
                        FROM topes_transferencia
                        WHERE cliente_id = id_cliente);

    -- Si no tiene, se usa el tope global del banco (cliente_id nulo)
    IF tope_individual IS NULL THEN
      tope_individual := (SELECT MIN(monto_maximo_individual)
                          FROM topes_transferencia
                          WHERE cliente_id IS NULL);
    END IF;

    IF tope_individual IS NOT NULL AND NEW.monto > tope_individual THEN
      RAISE EXCEPTION 'La transferencia de % supera el tope individual permitido de %', NEW.monto, tope_individual;
    END IF;

  END IF;

  RETURN NEW;
END;

$function$;

-- =====================================================
-- 2. VISTAS
-- =====================================================

-- vw_cuentas_clientes
CREATE OR REPLACE VIEW public.vw_cuentas_clientes AS
SELECT (cl.nombres::text || ' '::text) || cl.apellidos::text AS cliente,
    c.numero_cuenta,
    tc.nombre AS tipo_cuenta,
    c.saldo_disponible,
    e.nombre AS estado,
    c.fecha_apertura
   FROM cuentas c
     JOIN clientes cl ON cl.id = c.cliente_id
     JOIN tipo_cuenta tc ON tc.id = c.tipo_cuenta_id
     JOIN estados e ON e.id = c.estado_id;

-- vw_resumen_clientes
CREATE OR REPLACE VIEW public.vw_resumen_clientes AS
SELECT cl.id AS id_cliente,
    (cl.nombres::text || ' '::text) || cl.apellidos::text AS cliente,
    count(c.id) AS cantidad_cuentas,
    COALESCE(sum(c.saldo_disponible), 0::numeric) AS saldo_total
   FROM clientes cl
     LEFT JOIN cuentas c ON c.cliente_id = cl.id
  GROUP BY cl.id, cl.nombres, cl.apellidos;

-- vw_transacciones_detalle
CREATE OR REPLACE VIEW public.vw_transacciones_detalle AS
SELECT t.id AS id_transaccion,
    t.fecha_hora,
    tt.nombre AS tipo_transaccion,
    t.monto,
    co.numero_cuenta AS cuenta_origen,
    cd.numero_cuenta AS cuenta_destino,
    t.estado,
    t.descripcion
   FROM transacciones t
     JOIN tipo_transaccion tt ON tt.id = t.tipo_transaccion_id
     JOIN cuentas co ON co.id = t.cuenta_origen_id
     LEFT JOIN cuentas cd ON cd.id = t.cuenta_destino_id;

-- vw_usuarios_bloqueados
CREATE OR REPLACE VIEW public.vw_usuarios_bloqueados AS
SELECT u.id AS id_usuario,
    u.email,
    r.nombre AS rol,
    e.nombre AS estado,
    u.intentos_fallidos,
    u.bloqueado_hasta
   FROM usuarios u
     JOIN roles r ON r.id = u.rol_id
     JOIN estados e ON e.id = u.estado_id
  WHERE u.bloqueado_hasta > now();

-- =====================================================
-- 3. TRIGGERS
-- =====================================================

DROP TRIGGER IF EXISTS trg_eliminar_cliente ON public.clientes;
CREATE TRIGGER trg_eliminar_cliente BEFORE DELETE ON clientes FOR EACH ROW EXECUTE FUNCTION fn_eliminar_cliente();

DROP TRIGGER IF EXISTS trg_validar_cuenta_activa_por_tipo ON public.cuentas;
CREATE TRIGGER trg_validar_cuenta_activa_por_tipo BEFORE INSERT ON cuentas FOR EACH ROW EXECUTE FUNCTION fn_validar_cuenta_activa_por_tipo();

DROP TRIGGER IF EXISTS trg_validar_cuentas_activas ON public.transacciones;
CREATE TRIGGER trg_validar_cuentas_activas BEFORE INSERT ON transacciones FOR EACH ROW EXECUTE FUNCTION fn_validar_cuentas_activas();

DROP TRIGGER IF EXISTS trg_validar_tope_transferencia ON public.transacciones;
CREATE TRIGGER trg_validar_tope_transferencia BEFORE INSERT ON transacciones FOR EACH ROW EXECUTE FUNCTION fn_validar_tope_transferencia();

DROP TRIGGER IF EXISTS trg_bloquear_usuario ON public.usuarios;
CREATE TRIGGER trg_bloquear_usuario BEFORE UPDATE OF intentos_fallidos ON usuarios FOR EACH ROW EXECUTE FUNCTION fn_bloquear_usuario();


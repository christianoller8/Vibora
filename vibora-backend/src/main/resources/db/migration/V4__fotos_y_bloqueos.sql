-- V4: fotos de incidencias y bloqueo de pistas por mantenimiento (fase 3, rebanada 2).

-- Nuevos tipos de evento en el historial de una incidencia. El CHECK se recrea con el mismo nombre y la lista completa.
ALTER TABLE incidencia_evento DROP CONSTRAINT incidencia_evento_tipo_ck;
ALTER TABLE incidencia_evento ADD CONSTRAINT incidencia_evento_tipo_ck CHECK (tipo IN ('CREADA', 'ASIGNADA', 'ESTADO',
    'COMENTARIO', 'PRIORIDAD', 'FOTO_ANADIDA', 'FOTO_ELIMINADA', 'BLOQUEO_CREADO', 'BLOQUEO_LEVANTADO'));

-- Metadatos de una foto; los bytes viven fuera de la BD (almacen de ficheros). clave_almacen es un UUID aleatorio
-- generado por el servidor (nunca sale de lo que escribe quien sube); nombre_original es solo para mostrar.
-- tipo_contenido es el detectado por los bytes (no el que declara el cliente) y tamano el del fichero ya recodificado.
CREATE TABLE incidencia_foto (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    incidencia_id   bigint       NOT NULL REFERENCES incidencia (id),
    clave_almacen   varchar(80)  NOT NULL,
    nombre_original varchar(255) NOT NULL,
    tipo_contenido  varchar(20)  NOT NULL,
    tamano          integer      NOT NULL,
    subida_por      bigint       NOT NULL REFERENCES usuario (id),
    creada_en       timestamptz  NOT NULL,
    CONSTRAINT incidencia_foto_clave_uk UNIQUE (clave_almacen),
    CONSTRAINT incidencia_foto_tipo_ck CHECK (tipo_contenido IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT incidencia_foto_tamano_ck CHECK (tamano > 0 AND tamano <= 5242880)
);
CREATE INDEX incidencia_foto_incidencia_idx ON incidencia_foto (incidencia_id);

-- Una pista bloqueada no admite reservas nuevas en [desde, hasta) (hasta nulo = sin fin). Las reservas que ya existen
-- no se tocan: las decide el administrador. Un bloqueo esta ACTIVO si levantado_en es nulo y (hasta es nulo o hasta > ahora).
-- Que la incidencia sea del mismo club (y de la misma pista, si tiene) se comprueba en el servicio.
CREATE TABLE bloqueo_pista (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pista_id      bigint       NOT NULL REFERENCES pista (id),
    incidencia_id bigint REFERENCES incidencia (id),
    motivo        varchar(300) NOT NULL,
    desde         timestamptz  NOT NULL,
    hasta         timestamptz,
    creado_por    bigint       NOT NULL REFERENCES usuario (id),
    creado_en     timestamptz  NOT NULL,
    levantado_en  timestamptz,
    levantado_por bigint REFERENCES usuario (id),
    CONSTRAINT bloqueo_pista_rango_ck CHECK (hasta IS NULL OR hasta > desde)
);
CREATE INDEX bloqueo_pista_pista_levantado_idx ON bloqueo_pista (pista_id, levantado_en);
-- como mucho UN bloqueo sin levantar por incidencia (los levantados se acumulan como historial)
CREATE UNIQUE INDEX bloqueo_pista_incidencia_activo_uk ON bloqueo_pista (incidencia_id)
    WHERE incidencia_id IS NOT NULL AND levantado_en IS NULL;

-- V3: personal del club e incidencias (fase 3, rebanada 1): incidencia, incidencia_evento y nuevos tipos de aviso.

-- Nuevos tipos de notificacion (personal e incidencias). El CHECK se recrea con el mismo nombre y la lista completa.
ALTER TABLE notificacion DROP CONSTRAINT notificacion_tipo_ck;
ALTER TABLE notificacion ADD CONSTRAINT notificacion_tipo_ck CHECK (tipo IN ('JUGADOR_SE_APUNTO', 'JUGADOR_SALIO',
    'PARTIDO_COMPLETO', 'CARGO_NUEVO', 'PAGO_RECIBIDO', 'RESERVA_CANCELADA',
    'PERSONAL_ANADIDO', 'PERSONAL_QUITADO', 'INCIDENCIA_NUEVA', 'INCIDENCIA_ASIGNADA', 'INCIDENCIA_ESTADO',
    'INCIDENCIA_COMENTARIO'));

-- Una averia o problema reportado en un club (opcionalmente sobre una pista). Nunca se borra: se cierra o rechaza.
-- Que la pista pertenezca al club se comprueba en el servicio.
CREATE TABLE incidencia (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    club_id        bigint        NOT NULL REFERENCES club (id),
    pista_id       bigint REFERENCES pista (id),
    reportada_por  bigint        NOT NULL REFERENCES usuario (id),
    titulo         varchar(120)  NOT NULL,
    descripcion    varchar(2000) NOT NULL,
    categoria      varchar(20)   NOT NULL,
    prioridad      varchar(20)   NOT NULL DEFAULT 'MEDIA',
    estado         varchar(20)   NOT NULL DEFAULT 'ABIERTA',
    asignada_a     bigint REFERENCES usuario (id),
    -- bloqueo optimista (@Version): dos cambios simultaneos sobre la misma incidencia no se pisan
    version        bigint        NOT NULL DEFAULT 0,
    creada_en      timestamptz   NOT NULL,
    actualizada_en timestamptz   NOT NULL,
    resuelta_en    timestamptz,
    CONSTRAINT incidencia_categoria_ck CHECK (categoria IN ('RED', 'ILUMINACION', 'CRISTAL', 'SUELO', 'OTRA')),
    CONSTRAINT incidencia_prioridad_ck CHECK (prioridad IN ('BAJA', 'MEDIA', 'ALTA', 'URGENTE')),
    CONSTRAINT incidencia_estado_ck CHECK (estado IN ('ABIERTA', 'ASIGNADA', 'EN_CURSO', 'RESUELTA', 'CERRADA',
        'RECHAZADA')),
    -- una incidencia asignada o en curso siempre tiene responsable
    CONSTRAINT incidencia_asignada_ck CHECK (estado NOT IN ('ASIGNADA', 'EN_CURSO') OR asignada_a IS NOT NULL)
);
CREATE INDEX incidencia_club_estado_idx ON incidencia (club_id, estado);
CREATE INDEX incidencia_reportada_por_idx ON incidencia (reportada_por);
CREATE INDEX incidencia_asignada_a_idx ON incidencia (asignada_a);
CREATE INDEX incidencia_pista_id_idx ON incidencia (pista_id);

-- Historial inmutable de lo que pasa en una incidencia (creacion, asignacion, cambios de estado, comentarios).
CREATE TABLE incidencia_evento (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    incidencia_id   bigint        NOT NULL REFERENCES incidencia (id),
    usuario_id      bigint        NOT NULL REFERENCES usuario (id),
    tipo            varchar(20)   NOT NULL,
    estado_anterior varchar(20),
    estado_nuevo    varchar(20),
    texto           varchar(2000),
    creado_en       timestamptz   NOT NULL,
    CONSTRAINT incidencia_evento_tipo_ck CHECK (tipo IN ('CREADA', 'ASIGNADA', 'ESTADO', 'COMENTARIO', 'PRIORIDAD'))
);
CREATE INDEX incidencia_evento_incidencia_creado_idx ON incidencia_evento (incidencia_id, creado_en);

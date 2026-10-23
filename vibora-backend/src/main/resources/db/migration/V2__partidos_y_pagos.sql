-- V2: partidos abiertos y pagos compartidos (fase 2): participante, cargo, pago, notificacion.

-- Una reserva puede marcarse como partido abierto: otros jugadores pueden apuntarse mientras queden plazas.
ALTER TABLE reserva ADD COLUMN abierta boolean NOT NULL DEFAULT false;

-- Jugadores de una reserva (maximo 4). El organizador (reserva.usuario_id) es siempre el puesto 1.
CREATE TABLE participante (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reserva_id bigint      NOT NULL REFERENCES reserva (id),
    usuario_id bigint      NOT NULL REFERENCES usuario (id),
    puesto     smallint    NOT NULL,
    unido_en   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT participante_puesto_ck CHECK (puesto BETWEEN 1 AND 4),
    CONSTRAINT participante_puesto_uk UNIQUE (reserva_id, puesto),
    CONSTRAINT participante_usuario_uk UNIQUE (reserva_id, usuario_id)
);
CREATE INDEX participante_usuario_id_idx ON participante (usuario_id);

-- Las reservas existentes: su organizador juega en el puesto 1.
INSERT INTO participante (reserva_id, usuario_id, puesto, unido_en)
SELECT id, usuario_id, 1, creado_en FROM reserva;

-- Lo que un jugador debe al organizador por su parte de la pista.
CREATE TABLE cargo (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reserva_id  bigint        NOT NULL REFERENCES reserva (id),
    deudor_id   bigint        NOT NULL REFERENCES usuario (id),
    acreedor_id bigint        NOT NULL REFERENCES usuario (id),
    importe     numeric(8, 2) NOT NULL,
    estado      varchar(20)   NOT NULL DEFAULT 'PENDIENTE',
    creado_en   timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT cargo_importe_ck CHECK (importe > 0),
    CONSTRAINT cargo_estado_ck CHECK (estado IN ('PENDIENTE', 'PAGADO', 'ANULADO', 'REEMBOLSADO')),
    CONSTRAINT cargo_partes_ck CHECK (deudor_id <> acreedor_id)
);
-- Un solo cargo "vivo" por jugador y partido; quien salio (ANULADO) o fue reembolsado puede volver a apuntarse.
CREATE UNIQUE INDEX cargo_vivo_uk ON cargo (reserva_id, deudor_id) WHERE estado IN ('PENDIENTE', 'PAGADO');
CREATE INDEX cargo_deudor_id_idx ON cargo (deudor_id);
CREATE INDEX cargo_acreedor_id_idx ON cargo (acreedor_id);
CREATE INDEX cargo_reserva_id_idx ON cargo (reserva_id);

-- Pago simulado (Bizum de mentira) de un cargo. Un cargo se paga una sola vez.
CREATE TABLE pago (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cargo_id   bigint        NOT NULL REFERENCES cargo (id),
    importe    numeric(8, 2) NOT NULL,
    referencia varchar(100)  NOT NULL,
    creado_en  timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pago_cargo_uk UNIQUE (cargo_id),
    CONSTRAINT pago_importe_ck CHECK (importe > 0)
);

-- Notificaciones persistidas (la entrega en tiempo real llegara despues, por WebSocket).
CREATE TABLE notificacion (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id bigint       NOT NULL REFERENCES usuario (id),
    tipo       varchar(30)  NOT NULL,
    mensaje    varchar(500) NOT NULL,
    reserva_id bigint REFERENCES reserva (id),
    leida      boolean      NOT NULL DEFAULT false,
    creada_en  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT notificacion_tipo_ck CHECK (tipo IN ('JUGADOR_SE_APUNTO', 'JUGADOR_SALIO', 'PARTIDO_COMPLETO',
        'CARGO_NUEVO', 'PAGO_RECIBIDO', 'RESERVA_CANCELADA'))
);
CREATE INDEX notificacion_usuario_leida_idx ON notificacion (usuario_id, leida, creada_en DESC);
CREATE INDEX notificacion_reserva_id_idx ON notificacion (reserva_id);

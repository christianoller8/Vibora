-- V1: esquema inicial de Víbora (fase 1): club, usuario, pista, tarifa_franja, reserva.

-- btree_gist permite combinar igualdad (=) y rangos (&&) en una restriccion EXCLUDE.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE club (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre        varchar(255) NOT NULL,
    direccion     varchar(255),
    ciudad        varchar(255),
    hora_apertura time         NOT NULL,
    hora_cierre   time         NOT NULL
);

CREATE TABLE usuario (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         varchar(255) NOT NULL,
    password_hash varchar(255) NOT NULL,
    nombre        varchar(255),
    apellidos     varchar(255) NOT NULL,
    rol           varchar(20)  NOT NULL,
    club_id       bigint REFERENCES club (id),
    activo        boolean      NOT NULL DEFAULT true,
    creado_en     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT usuario_email_uk UNIQUE (email),
    CONSTRAINT usuario_rol_ck CHECK (rol IN ('JUGADOR', 'MONITOR', 'MANTENIMIENTO', 'ADMIN_CLUB')),
    -- el personal del club siempre pertenece a un club; solo el jugador puede ir sin club
    CONSTRAINT usuario_personal_club_ck CHECK (rol = 'JUGADOR' OR club_id IS NOT NULL)
);
CREATE INDEX usuario_club_id_idx ON usuario (club_id);

CREATE TABLE pista (
    id      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    club_id bigint       NOT NULL REFERENCES club (id),
    nombre  varchar(255) NOT NULL,
    tipo    varchar(20)  NOT NULL,
    activa  boolean      NOT NULL DEFAULT true,
    CONSTRAINT pista_club_nombre_uk UNIQUE (club_id, nombre),
    CONSTRAINT pista_tipo_ck CHECK (tipo IN ('INTERIOR', 'EXTERIOR', 'CUBIERTA'))
);
CREATE INDEX pista_club_id_idx ON pista (club_id);

CREATE TABLE tarifa_franja (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    club_id     bigint        NOT NULL REFERENCES club (id),
    dia_semana  smallint      NOT NULL,                 -- ISO: 1 = lunes ... 7 = domingo
    hora_inicio time          NOT NULL,
    hora_fin    time          NOT NULL,
    precio_hora numeric(8, 2) NOT NULL,
    tipo        varchar(20)   NOT NULL,
    CONSTRAINT tarifa_dia_ck CHECK (dia_semana BETWEEN 1 AND 7),
    CONSTRAINT tarifa_precio_ck CHECK (precio_hora >= 0),
    CONSTRAINT tarifa_tipo_ck CHECK (tipo IN ('NORMAL', 'HORA_PUNTA', 'LUZ_ARTIFICIAL')),
    CONSTRAINT tarifa_horas_ck CHECK (hora_fin > hora_inicio)
);
CREATE INDEX tarifa_franja_club_id_idx ON tarifa_franja (club_id);

CREATE TABLE reserva (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pista_id    bigint        NOT NULL REFERENCES pista (id),
    usuario_id  bigint        NOT NULL REFERENCES usuario (id),
    inicio      timestamptz   NOT NULL,
    fin         timestamptz   NOT NULL,
    estado      varchar(20)   NOT NULL DEFAULT 'CONFIRMADA',
    precio_total numeric(8, 2) NOT NULL,                -- precio congelado en el momento de reservar
    creado_en   timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT reserva_estado_ck CHECK (estado IN ('CONFIRMADA', 'CANCELADA')),
    CONSTRAINT reserva_precio_ck CHECK (precio_total >= 0),
    CONSTRAINT reserva_rango_ck CHECK (fin > inicio)
);
CREATE INDEX reserva_usuario_id_idx ON reserva (usuario_id);

-- REGLA CLAVE: una pista no puede tener dos reservas CONFIRMADAS que se solapen.
-- tstzrange(inicio, fin) es semiabierto [), asi que una reserva puede empezar justo
-- cuando termina la anterior. Las CANCELADAS quedan fuera (WHERE) y no bloquean nada.
-- El indice gist que respalda la restriccion tambien cubre las busquedas por pista y rango.
ALTER TABLE reserva ADD CONSTRAINT reserva_sin_solape
    EXCLUDE USING gist (pista_id WITH =, tstzrange(inicio, fin) WITH &&)
    WHERE (estado = 'CONFIRMADA');

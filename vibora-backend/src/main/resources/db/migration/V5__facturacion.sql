-- V5: facturacion simulada (fase 3, rebanada 3): datos fiscales del club, numeracion por club y anio, facturas
-- inmutables y su rectificativa. Toda factura es SIMULADA y asi lo dice su PDF ("Documento simulado sin validez fiscal").

-- Nuevo tipo de aviso: se ha emitido una factura rectificativa. El CHECK se recrea con el mismo nombre y la lista completa.
ALTER TABLE notificacion DROP CONSTRAINT notificacion_tipo_ck;
ALTER TABLE notificacion ADD CONSTRAINT notificacion_tipo_ck CHECK (tipo IN ('JUGADOR_SE_APUNTO', 'JUGADOR_SALIO',
    'PARTIDO_COMPLETO', 'CARGO_NUEVO', 'PAGO_RECIBIDO', 'RESERVA_CANCELADA',
    'PERSONAL_ANADIDO', 'PERSONAL_QUITADO', 'INCIDENCIA_NUEVA', 'INCIDENCIA_ASIGNADA', 'INCIDENCIA_ESTADO',
    'INCIDENCIA_COMENTARIO', 'FACTURA_RECTIFICATIVA'));

-- Datos fiscales del club (opcionales hasta que el administrador los configura) y su tipo de IVA (por defecto 21 %).
-- numeric(4, 2) llega hasta 99.99, asi que en la practica el tipo maximo es 99.99.
ALTER TABLE club
    ADD COLUMN razon_social     varchar(200),
    ADD COLUMN nif              varchar(15),
    ADD COLUMN direccion_fiscal varchar(300),
    ADD COLUMN tipo_iva         numeric(4, 2) NOT NULL DEFAULT 21.00,
    ADD CONSTRAINT club_tipo_iva_ck CHECK (tipo_iva >= 0 AND tipo_iva <= 100);

-- Contador de facturas por club y anio. Se bloquea con SELECT ... FOR UPDATE en la MISMA transaccion que inserta la
-- factura: si la transaccion se deshace, el contador tambien, y no queda ningun hueco en la numeracion.
CREATE TABLE factura_serie (
    club_id       bigint   NOT NULL REFERENCES club (id),
    anio          smallint NOT NULL,
    ultimo_numero integer  NOT NULL DEFAULT 0,
    CONSTRAINT factura_serie_pk PRIMARY KEY (club_id, anio),
    CONSTRAINT factura_serie_ultimo_ck CHECK (ultimo_numero >= 0)
);

-- Una factura emitida. Los datos del emisor y del receptor son una COPIA del momento de emitirla. El receptor es el
-- organizador de la reserva (las deudas entre jugadores son privadas y no se facturan). Una rectificativa lleva los
-- importes en negativo y apunta a la original.
CREATE TABLE factura (
    id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    club_id             bigint        NOT NULL REFERENCES club (id),
    reserva_id          bigint        NOT NULL REFERENCES reserva (id),
    receptor_usuario_id bigint        NOT NULL REFERENCES usuario (id),
    serie               varchar(10)   NOT NULL,
    numero              integer       NOT NULL,
    tipo                varchar(20)   NOT NULL,
    rectifica_a         bigint REFERENCES factura (id),
    emitida_en          timestamptz   NOT NULL,
    emisor_razon_social varchar(200)  NOT NULL,
    emisor_nif          varchar(15)   NOT NULL,
    emisor_direccion    varchar(300)  NOT NULL,
    receptor_nombre     varchar(200)  NOT NULL,
    receptor_nif        varchar(15),
    receptor_direccion  varchar(300),
    concepto            varchar(300)  NOT NULL,
    base                numeric(10, 2) NOT NULL,
    tipo_iva            numeric(4, 2)  NOT NULL,
    cuota_iva           numeric(10, 2) NOT NULL,
    total               numeric(10, 2) NOT NULL,
    CONSTRAINT factura_numero_ck CHECK (numero > 0),
    CONSTRAINT factura_tipo_ck CHECK (tipo IN ('ORIGINAL', 'RECTIFICATIVA')),
    -- original: total positivo y sin referencia; rectificativa: total negativo y con referencia a la original
    CONSTRAINT factura_total_signo_ck CHECK (
        (tipo = 'ORIGINAL' AND total > 0 AND rectifica_a IS NULL)
        OR (tipo = 'RECTIFICATIVA' AND total < 0 AND rectifica_a IS NOT NULL)),
    CONSTRAINT factura_base_cuota_ck CHECK (base + cuota_iva = total),
    CONSTRAINT factura_tipo_iva_ck CHECK (tipo_iva >= 0 AND tipo_iva <= 100),
    -- numeracion unica por club y serie (la serie lleva el anio)
    CONSTRAINT factura_numero_uk UNIQUE (club_id, serie, numero),
    -- como mucho UNA rectificativa por factura
    CONSTRAINT factura_rectifica_uk UNIQUE (rectifica_a)
);
-- como mucho UNA factura original por reserva (las rectificativas comparten reserva con su original)
CREATE UNIQUE INDEX factura_original_reserva_uk ON factura (reserva_id) WHERE tipo = 'ORIGINAL';
CREATE INDEX factura_reserva_idx ON factura (reserva_id);
CREATE INDEX factura_club_emitida_idx ON factura (club_id, emitida_en);
CREATE INDEX factura_receptor_idx ON factura (receptor_usuario_id, emitida_en);

-- Las facturas emitidas NO se modifican ni se borran nunca (una correccion es una rectificativa nueva). Lo impone la
-- base de datos con un trigger, no solo la aplicacion. TRUNCATE no pasa por este trigger (hace falta ser propietario
-- de la tabla): los tests lo usan para limpiar.
CREATE FUNCTION factura_inmutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Las facturas son inmutables: no se pueden modificar ni borrar (factura %)', OLD.id
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER factura_inmutable_tg
    BEFORE UPDATE OR DELETE ON factura
    FOR EACH ROW EXECUTE FUNCTION factura_inmutable();

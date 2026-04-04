CREATE TABLE trip (
    id                 UUID          PRIMARY KEY,
    title              VARCHAR(255)  NOT NULL,
    description        TEXT,
    cover_image_key    VARCHAR(500),
    status             VARCHAR(50)   NOT NULL DEFAULT 'PLANNING',
    created_by         UUID          NOT NULL,
    reference_currency VARCHAR(10)   NOT NULL DEFAULT 'EUR',
    start_date         DATE,
    end_date           DATE,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    deleted_at         TIMESTAMPTZ
);

CREATE TABLE trip_member (
    id           UUID         PRIMARY KEY,
    trip_id      UUID         NOT NULL REFERENCES trip(id),
    device_id    UUID         NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    role         VARCHAR(50)  NOT NULL,
    joined_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at   TIMESTAMPTZ,
    CONSTRAINT uq_trip_member UNIQUE (trip_id, device_id)
);

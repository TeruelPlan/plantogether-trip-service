-- trip service: initial schema
-- Only user_profile table; trip and trip_member tables added in V2__trip.sql

CREATE TABLE user_profile (
    device_id    UUID          PRIMARY KEY,
    display_name VARCHAR(255)  NOT NULL,
    avatar_url   VARCHAR(512),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

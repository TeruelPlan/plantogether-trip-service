CREATE TABLE trip_invitation (
    id         UUID          PRIMARY KEY,
    trip_id    UUID          NOT NULL REFERENCES trip(id),
    token      UUID          NOT NULL UNIQUE,
    created_by UUID          NOT NULL,  -- device_id of organizer
    created_at TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trip_invitation_token ON trip_invitation(token);
CREATE INDEX idx_trip_invitation_trip_id ON trip_invitation(trip_id);

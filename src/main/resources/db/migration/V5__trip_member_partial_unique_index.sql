-- Replace the full unique constraint with a partial one so soft-deleted members
-- can re-join the same trip (deleted_at IS NOT NULL rows are excluded).
ALTER TABLE trip_member DROP CONSTRAINT uq_trip_member;
CREATE UNIQUE INDEX uq_trip_member_active ON trip_member (trip_id, device_id) WHERE deleted_at IS NULL;

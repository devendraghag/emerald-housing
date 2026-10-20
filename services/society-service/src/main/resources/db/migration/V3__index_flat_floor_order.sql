-- The common floor listing filters by floor_id and orders by flat_number.
-- The composite index serves both operations and replaces the narrower index.
DROP INDEX IF EXISTS ix_flats_floor;
CREATE INDEX ix_flats_floor_flat_number ON flats (floor_id, flat_number);

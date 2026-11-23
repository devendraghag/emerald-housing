-- Flat directory search uses lower(flat_number) LIKE '%term%'.
-- Its floor/order B-tree cannot accelerate this leading-wildcard lookup.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX ix_flats_flat_number_trgm
    ON flats USING gin (lower(flat_number) gin_trgm_ops);

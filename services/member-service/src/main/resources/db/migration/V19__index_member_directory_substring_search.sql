-- Member directory search uses lower(column) LIKE '%term%' over these fields.
-- B-tree indexes cannot accelerate a leading-wildcard pattern; pg_trgm GIN indexes can.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX ix_members_full_name_trgm
    ON members USING gin (lower(full_name) gin_trgm_ops);

CREATE INDEX ix_members_mobile_number_trgm
    ON members USING gin (lower(mobile_number) gin_trgm_ops);

CREATE INDEX ix_members_email_trgm
    ON members USING gin (lower(email) gin_trgm_ops);

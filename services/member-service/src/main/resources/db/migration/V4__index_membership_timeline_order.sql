-- Membership history endpoints filter by member/flat and default to start_date DESC.
-- Leading filter columns also preserve equality lookups and replace the narrower indexes.
DROP INDEX IF EXISTS ix_memberships_member;
DROP INDEX IF EXISTS ix_memberships_flat;
CREATE INDEX ix_memberships_member_start_date ON flat_memberships (member_id, start_date DESC);
CREATE INDEX ix_memberships_flat_start_date ON flat_memberships (flat_id, start_date DESC);

-- Support keyset continuation for member and flat membership histories.
CREATE INDEX ix_memberships_member_cursor
    ON flat_memberships (member_id, created_at DESC, id DESC);

CREATE INDEX ix_memberships_flat_cursor
    ON flat_memberships (flat_id, created_at DESC, id DESC);

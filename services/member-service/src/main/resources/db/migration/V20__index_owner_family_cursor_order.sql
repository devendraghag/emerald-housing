-- Support stable cursor traversal for the owner and family directories.
CREATE INDEX ix_owner_membership_cursor
    ON flat_memberships (start_date DESC, created_at DESC, id DESC)
    WHERE relationship_type = 'OWNER';

CREATE INDEX ix_family_cursor_order
    ON family_members (created_at ASC, id ASC);

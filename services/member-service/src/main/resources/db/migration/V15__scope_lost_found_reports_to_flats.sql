-- Existing reports have no reliable flat assignment. Keep them available to
-- administrators while resident queries fail closed for rows without a flat.
ALTER TABLE lost_found_reports ADD COLUMN flat_id UUID;

CREATE INDEX lost_found_flat_board_idx
    ON lost_found_reports(flat_id, status, created_at DESC);

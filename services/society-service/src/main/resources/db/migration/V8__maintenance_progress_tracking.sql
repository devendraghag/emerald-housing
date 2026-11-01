CREATE TABLE maintenance_progress_updates (
    id UUID PRIMARY KEY,
    maintenance_id UUID NOT NULL REFERENCES rental_operations(id) ON DELETE CASCADE,
    note VARCHAR(2000) NOT NULL CHECK (length(trim(note)) > 0),
    status VARCHAR(24) NOT NULL,
    assigned_to VARCHAR(120),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_maintenance_progress_timeline
    ON maintenance_progress_updates(maintenance_id, created_at, id);

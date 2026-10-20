CREATE TABLE audit_events (
 sequence BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
 id UUID PRIMARY KEY, actor_id UUID, actor_name VARCHAR(120) NOT NULL,
 action VARCHAR(64) NOT NULL, entity_type VARCHAR(40) NOT NULL, entity_id UUID NOT NULL,
 before_state TEXT, after_state TEXT, occurred_at TIMESTAMPTZ NOT NULL, correlation_id VARCHAR(64)
);
CREATE INDEX ix_audit_entity_sequence ON audit_events(entity_type,entity_id,sequence DESC);
ALTER TABLE flats ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
CREATE INDEX ix_flats_cursor ON flats(created_at,id);
CREATE INDEX ix_flats_floor_cursor ON flats(floor_id,created_at,id);
CREATE INDEX ix_flats_status_cursor ON flats(status,created_at,id);

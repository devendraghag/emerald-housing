CREATE TABLE audit_events (
 sequence BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
 id UUID PRIMARY KEY, actor_id UUID, actor_name VARCHAR(120) NOT NULL,
 action VARCHAR(64) NOT NULL, entity_type VARCHAR(40) NOT NULL, entity_id UUID NOT NULL,
 before_state TEXT, after_state TEXT, occurred_at TIMESTAMPTZ NOT NULL, correlation_id VARCHAR(64)
);
CREATE INDEX ix_audit_entity_sequence ON audit_events(entity_type,entity_id,sequence DESC);
ALTER TABLE members ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE flat_memberships ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE admin_users ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE admin_users ADD COLUMN society_code VARCHAR(40) NOT NULL DEFAULT 'EMERALD';
ALTER TABLE admin_users ADD COLUMN member_id UUID REFERENCES members(id);
ALTER TABLE admin_users DROP CONSTRAINT admin_users_role_check;
ALTER TABLE admin_users ADD CONSTRAINT admin_users_role_check CHECK(role IN ('ADMIN','SOCIETY_ADMIN','COMMITTEE','RESIDENT'));
ALTER TABLE admin_users ADD CONSTRAINT resident_requires_member CHECK(role <> 'RESIDENT' OR member_id IS NOT NULL);
CREATE INDEX ix_members_cursor ON members(created_at,id);
CREATE INDEX ix_members_status_cursor ON members(status,created_at,id);
CREATE INDEX ix_memberships_resident_access ON flat_memberships(member_id,flat_id) WHERE end_date IS NULL;

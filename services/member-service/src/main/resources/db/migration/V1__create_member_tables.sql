CREATE TABLE members (
    id UUID PRIMARY KEY,
    full_name VARCHAR(160) NOT NULL,
    mobile_number VARCHAR(10) NOT NULL,
    email VARCHAR(254),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX uq_members_active_mobile ON members(mobile_number) WHERE status = 'ACTIVE';
CREATE INDEX ix_members_email ON members(email);

CREATE TABLE flat_memberships (
    id UUID PRIMARY KEY,
    member_id UUID NOT NULL REFERENCES members(id),
    flat_id UUID NOT NULL,
    relationship_type VARCHAR(20) NOT NULL CHECK (relationship_type IN ('OWNER', 'TENANT', 'RESIDENT')),
    start_date DATE NOT NULL,
    end_date DATE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_membership_dates CHECK (end_date IS NULL OR end_date >= start_date)
);
CREATE INDEX ix_memberships_member ON flat_memberships(member_id);
CREATE INDEX ix_memberships_flat ON flat_memberships(flat_id);
CREATE UNIQUE INDEX uq_active_membership ON flat_memberships(member_id, flat_id, relationship_type) WHERE end_date IS NULL;

CREATE TABLE rental_operations (
    id UUID PRIMARY KEY,
    operation_type VARCHAR(24) NOT NULL CHECK (operation_type IN ('PAYMENT','MAINTENANCE','LEASE','VIEWING')),
    flat_id UUID NOT NULL REFERENCES flats(id),
    member_id UUID,
    status VARCHAR(24) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_rental_operations_flat_type ON rental_operations(flat_id, operation_type, created_at DESC);
CREATE INDEX ix_rental_operations_member ON rental_operations(member_id, operation_type, created_at DESC);

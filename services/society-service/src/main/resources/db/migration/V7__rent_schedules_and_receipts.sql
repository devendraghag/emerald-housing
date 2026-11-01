CREATE TABLE rent_schedules (
    id UUID PRIMARY KEY,
    flat_id UUID NOT NULL REFERENCES flats(id),
    amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    due_day SMALLINT NOT NULL CHECK (due_day BETWEEN 1 AND 31),
    start_period VARCHAR(7) NOT NULL CHECK (start_period ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    end_period VARCHAR(7) CHECK (end_period IS NULL OR end_period ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (end_period IS NULL OR end_period >= start_period)
);
CREATE UNIQUE INDEX uq_active_rent_schedule_flat ON rent_schedules(flat_id) WHERE active;
CREATE INDEX ix_rent_schedules_period ON rent_schedules(active,start_period,end_period);

CREATE TABLE rent_payment_receipts (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES rental_operations(id),
    amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    reference VARCHAR(120) NOT NULL,
    received_at DATE NOT NULL,
    reconciled BOOLEAN NOT NULL DEFAULT FALSE,
    reconciliation_reference VARCHAR(120),
    reconciled_at TIMESTAMPTZ,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((reconciled = FALSE AND reconciliation_reference IS NULL AND reconciled_at IS NULL)
        OR (reconciled = TRUE AND reconciliation_reference IS NOT NULL AND reconciled_at IS NOT NULL))
);
CREATE UNIQUE INDEX uq_rent_receipt_reference ON rent_payment_receipts(reference);
CREATE UNIQUE INDEX uq_rent_reconciliation_reference ON rent_payment_receipts(reconciliation_reference) WHERE reconciliation_reference IS NOT NULL;
CREATE INDEX ix_rent_receipts_payment ON rent_payment_receipts(payment_id,received_at,id);

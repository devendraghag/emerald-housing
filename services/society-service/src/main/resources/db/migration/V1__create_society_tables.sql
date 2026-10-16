CREATE TABLE societies (
    id UUID PRIMARY KEY,
    society_code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE floors (
    id UUID PRIMARY KEY,
    society_id UUID NOT NULL REFERENCES societies(id),
    floor_number INTEGER NOT NULL CHECK (floor_number BETWEEN 1 AND 18),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_floor_society_number UNIQUE (society_id, floor_number),
    CONSTRAINT uq_floor_id_society UNIQUE (id, society_id)
);

CREATE TABLE flats (
    id UUID PRIMARY KEY,
    society_id UUID NOT NULL REFERENCES societies(id),
    floor_id UUID NOT NULL,
    flat_number VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_flat_floor_society FOREIGN KEY (floor_id, society_id) REFERENCES floors(id, society_id),
    CONSTRAINT uq_flat_society_number UNIQUE (society_id, flat_number)
);
CREATE INDEX ix_flats_floor ON flats(floor_id);

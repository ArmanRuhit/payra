-- =====================================================================
-- V1 — users: the identity root table.
-- One row per phone number; everything else (wallet, KYC, PIN) hangs off it.
-- =====================================================================
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number VARCHAR(20) NOT NULL,
    full_name VARCHAR(120),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_phone_number UNIQUE (phone_number),
    CONSTRAINT ck_users_status CHECK (
        status IN (
            'PENDING_VERIFICATION',
            'ACTIVE',
            'SUSPENDED',
            'CLOSED'
        )
    )
);
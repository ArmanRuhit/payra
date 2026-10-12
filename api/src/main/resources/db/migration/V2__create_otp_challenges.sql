-- =====================================================================
-- V2 - otp_challenges: one row per OTP request.
-- Codes are stored HASHED (bcrypt) - the raw 6-digit code only ever exists
-- in the delivery channel (in dev: the application log).
-- =====================================================================

CREATE TABLE otp_challenges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number VARCHAR(20) NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ NOT NULL,
    verified_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_otp_challenges_status CHECK (status in ('PENDING', 'VERIFIED', 'EXPIRED'))
);

-- Verify hot path: latest pending challenge for one phone number.
CREATE INDEX ix_otp_challenges_phone_status ON otp_challenges (phone_number, status);
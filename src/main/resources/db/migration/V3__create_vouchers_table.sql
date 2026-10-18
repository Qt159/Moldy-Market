-- ============================================================
-- V3 — DDL: vouchers table
-- ============================================================

CREATE TABLE IF NOT EXISTS vouchers (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(50)  NOT NULL UNIQUE,
    discount_type   VARCHAR(10)  NOT NULL CHECK (discount_type IN ('PERCENT', 'FIXED')),
    discount_value  DECIMAL(19,2) NOT NULL CHECK (discount_value > 0),
    remaining_quantity INTEGER   NOT NULL CHECK (remaining_quantity >= 0),
    version         INTEGER      NOT NULL DEFAULT 0,
    expired_at      TIMESTAMPTZ  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_voucher_code ON vouchers(code);
CREATE INDEX IF NOT EXISTS idx_voucher_expired_at ON vouchers(expired_at);

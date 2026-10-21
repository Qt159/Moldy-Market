-- ============================================================
-- V2 — DDL: Pricing Engine Tables
-- reference_prices, pricing_requests, appraiser_profiles, appraiser_categories
-- ============================================================

-- ============================================================
-- 1. reference_prices — Giá tham chiếu của model sản phẩm
-- ============================================================
CREATE TABLE IF NOT EXISTS reference_prices (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id     UUID         NOT NULL REFERENCES categories(id),
    brand           VARCHAR(100) NOT NULL,
    model_name      VARCHAR(200) NOT NULL,
    reference_price BIGINT       NOT NULL CHECK (reference_price > 0),
    currency        VARCHAR(10)  NOT NULL DEFAULT 'VND',
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by      UUID         REFERENCES users(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_brand_model UNIQUE (brand, model_name)
);

CREATE INDEX IF NOT EXISTS idx_ref_prices_category ON reference_prices(category_id);
CREATE INDEX IF NOT EXISTS idx_ref_prices_brand_model ON reference_prices(brand, model_name);
CREATE INDEX IF NOT EXISTS idx_ref_prices_updated_at ON reference_prices(updated_at DESC);

-- ============================================================
-- 2. pricing_mode — Enum: AUTO, APPRAISER
-- ============================================================
CREATE TYPE pricing_mode AS ENUM ('AUTO', 'APPRAISER');

-- ============================================================
-- 3. pricing_status — Enum: SUGGESTED, MANUAL_REQUIRED, PENDING, SELLER_REJECTED, EXPIRED
-- ============================================================
CREATE TYPE pricing_status AS ENUM (
    'SUGGESTED',
    'MANUAL_REQUIRED',
    'PENDING',
    'SELLER_REJECTED',
    'EXPIRED'
);

-- ============================================================
-- 4. warranty_status — Enum: UNDER_WARRANTY, EXPIRED_UNDER_1Y, EXPIRED_OVER_1Y
-- ============================================================
CREATE TYPE warranty_status_enum AS ENUM (
    'UNDER_WARRANTY',
    'EXPIRED_UNDER_1Y',
    'EXPIRED_OVER_1Y'
);

-- ============================================================
-- 5. condition_level — Enum: NEW, SCRATCHED, PARTIAL_DAMAGE, HEAVILY_DAMAGED
-- ============================================================
CREATE TYPE condition_level_enum AS ENUM (
    'NEW',
    'SCRATCHED',
    'PARTIAL_DAMAGE',
    'HEAVILY_DAMAGED'
);

-- ============================================================
-- 6. pricing_requests — Yêu cầu định giá
-- ============================================================
CREATE TABLE IF NOT EXISTS pricing_requests (
    id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    pricing_mode        pricing_mode   NOT NULL,
    status              pricing_status NOT NULL,

    -- Linking (listing support will be added later when Listing entity created)
    -- listing_id       UUID           REFERENCES listings(id),
    -- For now, store as nullable UUID placeholder
    listing_id          UUID           NULL,
    seller_id           UUID           NOT NULL REFERENCES users(id),

    -- Auto Pricing Input
    category_id         UUID           NULL REFERENCES categories(id),
    model_id            UUID           NULL REFERENCES reference_prices(id),
    origin              VARCHAR(20)    NULL,  -- OFFICIAL_VN
    warranty_status     warranty_status_enum NULL,
    condition_level     condition_level_enum NULL,
    usage_months        INTEGER        NULL CHECK (usage_months >= 0),

    -- Auto Pricing Output
    suggested_price     BIGINT         NULL,
    reference_price     BIGINT         NULL,
    deprecation_factor  NUMERIC(5,4)   NULL,
    condition_factor    NUMERIC(5,4)   NULL,
    warranty_factor     NUMERIC(5,4)   NULL,
    reference_model_name VARCHAR(200)  NULL,
    reference_price_updated_at TIMESTAMPTZ NULL,

    -- Appraiser Pricing
    appraiser_id        UUID           NULL REFERENCES users(id),
    appraiser_note      TEXT           NULL,
    commission_rate     NUMERIC(5,2)   NULL CHECK (commission_rate >= 0 AND commission_rate <= 100),
    reject_count        SMALLINT       NOT NULL DEFAULT 0 CHECK (reject_count >= 0 AND reject_count <= 3),

    -- Disclaimer
    disclaimer          TEXT           NULL,

    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    
    CONSTRAINT check_pricing_input CHECK (
        -- Auto pricing: must have category_id, model_id, origin, condition_level, warranty_status, usage_months
        (pricing_mode = 'AUTO' AND category_id IS NOT NULL AND model_id IS NOT NULL AND condition_level IS NOT NULL)
        -- Appraiser pricing: must have appraiser_id
        OR (pricing_mode = 'APPRAISER' AND appraiser_id IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_pricing_requests_seller ON pricing_requests(seller_id);
CREATE INDEX IF NOT EXISTS idx_pricing_requests_appraiser ON pricing_requests(appraiser_id);
CREATE INDEX IF NOT EXISTS idx_pricing_requests_status ON pricing_requests(status);
CREATE INDEX IF NOT EXISTS idx_pricing_requests_mode ON pricing_requests(pricing_mode);
CREATE INDEX IF NOT EXISTS idx_pricing_requests_created_at ON pricing_requests(created_at DESC);

-- ============================================================
-- 7. appraiser_status — Enum: PENDING, ACTIVE, SUSPENDED
-- ============================================================
CREATE TYPE appraiser_status_enum AS ENUM ('PENDING', 'ACTIVE', 'SUSPENDED');

-- ============================================================
-- 8. appraiser_profiles — Hồ sơ người định giá
-- ============================================================
CREATE TABLE IF NOT EXISTS appraiser_profiles (
    id               UUID                 PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID                 NOT NULL UNIQUE REFERENCES users(id),
    status           appraiser_status_enum NOT NULL DEFAULT 'PENDING',
    total_completed  INTEGER              NOT NULL DEFAULT 0 CHECK (total_completed >= 0),
    accurate_count   INTEGER              NOT NULL DEFAULT 0 CHECK (accurate_count >= 0),
    created_at       TIMESTAMPTZ          NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ          NOT NULL DEFAULT NOW(),

    CONSTRAINT check_accurate_le_total CHECK (accurate_count <= total_completed)
);

CREATE INDEX IF NOT EXISTS idx_appraiser_profiles_status ON appraiser_profiles(status);
CREATE INDEX IF NOT EXISTS idx_appraiser_profiles_user_id ON appraiser_profiles(user_id);

-- ============================================================
-- 9. appraiser_categories — Danh sách category người định giá được phép làm
-- ============================================================
CREATE TABLE IF NOT EXISTS appraiser_categories (
    appraiser_id UUID NOT NULL REFERENCES appraiser_profiles(id) ON DELETE CASCADE,
    category_id  UUID NOT NULL REFERENCES categories(id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    PRIMARY KEY (appraiser_id, category_id)
);

CREATE INDEX IF NOT EXISTS idx_appraiser_categories_category ON appraiser_categories(category_id);

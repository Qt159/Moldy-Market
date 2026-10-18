-- ============================================================
-- V1 — DDL: categories table
-- ============================================================
-- Chạy script này trước V2__seed_categories.sql.
--
-- spring.jpa.hibernate.ddl-auto=validate yêu cầu schema phải
-- tồn tại trước khi app start. Chạy tay qua psql hoặc DBeaver,
-- hoặc tích hợp Flyway sau này.
-- ============================================================

CREATE TABLE IF NOT EXISTS categories (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    parent_id   UUID         REFERENCES categories(id) ON DELETE RESTRICT,
    level       SMALLINT     NOT NULL CHECK (level BETWEEN 1 AND 2),
    status      VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE'
                             CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_name_per_parent UNIQUE (parent_id, name)
);

CREATE INDEX IF NOT EXISTS idx_categories_parent_id ON categories(parent_id);
CREATE INDEX IF NOT EXISTS idx_categories_status    ON categories(status);
CREATE INDEX IF NOT EXISTS idx_categories_level     ON categories(level);

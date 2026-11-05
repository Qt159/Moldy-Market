-- ============================================================
-- DDL: categories table
-- 2 cấp: Root (level=1) → Subcategory (level=2)
-- Product entity sẽ tham chiếu đến Subcategory (level=2).
-- ============================================================

CREATE TABLE IF NOT EXISTS categories (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    slug        VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    parent_id   UUID         REFERENCES categories(id) ON DELETE RESTRICT,
    level       SMALLINT     NOT NULL CHECK (level BETWEEN 1 AND 2),
    status      VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_slug_per_parent UNIQUE (parent_id, slug)
);

CREATE INDEX IF NOT EXISTS idx_categories_parent_id ON categories(parent_id);
CREATE INDEX IF NOT EXISTS idx_categories_status    ON categories(status);
CREATE INDEX IF NOT EXISTS idx_categories_level     ON categories(level);

-- ============================================================
-- SEED DATA — idempotent (ON CONFLICT DO NOTHING)
-- ============================================================

-- ============================================================
-- Level 1: Root Categories
-- ============================================================
INSERT INTO categories (id, name, slug, description, parent_id, level, status) VALUES
  ('00000000-0000-0000-0000-000000000001', 'Đồ điện tử',  'do-dien-tu',  'Thiết bị điện tử, công nghệ',             NULL, 1, 'ACTIVE'),
  ('00000000-0000-0000-0000-000000000002', 'Đồ nội thất', 'do-noi-that', 'Nội thất, đồ dùng gia đình',             NULL, 1, 'ACTIVE'),
  ('00000000-0000-0000-0000-000000000003', 'Thời trang',  'thoi-trang',  'Quần áo, giày dép, phụ kiện thời trang', NULL, 1, 'ACTIVE'),
  ('00000000-0000-0000-0000-000000000004', 'Sách',        'sach',        'Sách, tài liệu học tập',                 NULL, 1, 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- Level 2: Subcategories — Đồ điện tử
-- ============================================================
INSERT INTO categories (id, name, slug, description, parent_id, level, status) VALUES
  ('00000000-0000-0001-0000-000000000001', 'Điện thoại',         'dien-thoai',         NULL, '00000000-0000-0000-0000-000000000001', 2, 'ACTIVE'),
  ('00000000-0000-0001-0000-000000000002', 'Laptop',             'laptop',             NULL, '00000000-0000-0000-0000-000000000001', 2, 'ACTIVE'),
  ('00000000-0000-0001-0000-000000000003', 'Máy tính bảng',      'may-tinh-bang',      NULL, '00000000-0000-0000-0000-000000000001', 2, 'ACTIVE'),
  ('00000000-0000-0001-0000-000000000004', 'Máy tính để bàn',    'may-tinh-de-ban',    NULL, '00000000-0000-0000-0000-000000000001', 2, 'ACTIVE'),
  ('00000000-0000-0001-0000-000000000005', 'Màn hình',           'man-hinh',           NULL, '00000000-0000-0000-0000-000000000001', 2, 'ACTIVE'),
  ('00000000-0000-0001-0000-000000000006', 'Máy ảnh',            'may-anh',            NULL, '00000000-0000-0000-0000-000000000001', 2, 'ACTIVE'),
  ('00000000-0000-0001-0000-000000000007', 'Thiết bị âm thanh',  'thiet-bi-am-thanh',  NULL, '00000000-0000-0000-0000-000000000001', 2, 'ACTIVE'),
  ('00000000-0000-0001-0000-000000000008', 'Thiết bị chơi game', 'thiet-bi-choi-game', NULL, '00000000-0000-0000-0000-000000000001', 2, 'ACTIVE'),
  ('00000000-0000-0001-0000-000000000009', 'Phụ kiện điện tử',   'phu-kien-dien-tu',   NULL, '00000000-0000-0000-0000-000000000001', 2, 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- Level 2: Subcategories — Đồ nội thất
-- ============================================================
INSERT INTO categories (id, name, slug, description, parent_id, level, status) VALUES
  ('00000000-0000-0002-0000-000000000001', 'Bàn',                'ban',                NULL, '00000000-0000-0000-0000-000000000002', 2, 'ACTIVE'),
  ('00000000-0000-0002-0000-000000000002', 'Ghế',                'ghe',                NULL, '00000000-0000-0000-0000-000000000002', 2, 'ACTIVE'),
  ('00000000-0000-0002-0000-000000000003', 'Giường',             'giuong',             NULL, '00000000-0000-0000-0000-000000000002', 2, 'ACTIVE'),
  ('00000000-0000-0002-0000-000000000004', 'Tủ',                 'tu',                 NULL, '00000000-0000-0000-0000-000000000002', 2, 'ACTIVE'),
  ('00000000-0000-0002-0000-000000000005', 'Kệ',                 'ke',                 NULL, '00000000-0000-0000-0000-000000000002', 2, 'ACTIVE'),
  ('00000000-0000-0002-0000-000000000006', 'Sofa',               'sofa',               NULL, '00000000-0000-0000-0000-000000000002', 2, 'ACTIVE'),
  ('00000000-0000-0002-0000-000000000007', 'Đèn chiếu sáng',    'den-chieu-sang',     NULL, '00000000-0000-0000-0000-000000000002', 2, 'ACTIVE'),
  ('00000000-0000-0002-0000-000000000008', 'Đồ trang trí',      'do-trang-tri',       NULL, '00000000-0000-0000-0000-000000000002', 2, 'ACTIVE'),
  ('00000000-0000-0002-0000-000000000009', 'Nội thất văn phòng', 'noi-that-van-phong', NULL, '00000000-0000-0000-0000-000000000002', 2, 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- Level 2: Subcategories — Thời trang
-- ============================================================
INSERT INTO categories (id, name, slug, description, parent_id, level, status) VALUES
  ('00000000-0000-0003-0000-000000000001', 'Quần áo nam',         'quan-ao-nam',         NULL, '00000000-0000-0000-0000-000000000003', 2, 'ACTIVE'),
  ('00000000-0000-0003-0000-000000000002', 'Quần áo nữ',          'quan-ao-nu',          NULL, '00000000-0000-0000-0000-000000000003', 2, 'ACTIVE'),
  ('00000000-0000-0003-0000-000000000003', 'Giày dép',            'giay-dep',            NULL, '00000000-0000-0000-0000-000000000003', 2, 'ACTIVE'),
  ('00000000-0000-0003-0000-000000000004', 'Túi xách',            'tui-xach',            NULL, '00000000-0000-0000-0000-000000000003', 2, 'ACTIVE'),
  ('00000000-0000-0003-0000-000000000005', 'Đồng hồ',             'dong-ho',             NULL, '00000000-0000-0000-0000-000000000003', 2, 'ACTIVE'),
  ('00000000-0000-0003-0000-000000000006', 'Phụ kiện thời trang', 'phu-kien-thoi-trang', NULL, '00000000-0000-0000-0000-000000000003', 2, 'ACTIVE'),
  ('00000000-0000-0003-0000-000000000007', 'Trang phục thể thao', 'trang-phuc-the-thao', NULL, '00000000-0000-0000-0000-000000000003', 2, 'ACTIVE'),
  ('00000000-0000-0003-0000-000000000008', 'Trang phục trẻ em',   'trang-phuc-tre-em',   NULL, '00000000-0000-0000-0000-000000000003', 2, 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- Level 2: Subcategories — Sách
-- ============================================================
INSERT INTO categories (id, name, slug, description, parent_id, level, status) VALUES
  ('00000000-0000-0004-0000-000000000001', 'Sách giáo khoa',                  'sach-giao-khoa',              NULL, '00000000-0000-0000-0000-000000000004', 2, 'ACTIVE'),
  ('00000000-0000-0004-0000-000000000002', 'Sách tham khảo',                  'sach-tham-khao',              NULL, '00000000-0000-0000-0000-000000000004', 2, 'ACTIVE'),
  ('00000000-0000-0004-0000-000000000003', 'Văn học',                         'van-hoc',                     NULL, '00000000-0000-0000-0000-000000000004', 2, 'ACTIVE'),
  ('00000000-0000-0004-0000-000000000004', 'Kinh tế – Kinh doanh',            'kinh-te-kinh-doanh',          NULL, '00000000-0000-0000-0000-000000000004', 2, 'ACTIVE'),
  ('00000000-0000-0004-0000-000000000005', 'Khoa học – Công nghệ',            'khoa-hoc-cong-nghe',          NULL, '00000000-0000-0000-0000-000000000004', 2, 'ACTIVE'),
  ('00000000-0000-0004-0000-000000000006', 'Lập trình – Công nghệ thông tin', 'lap-trinh-cong-nghe-thong-tin',NULL,'00000000-0000-0000-0000-000000000004', 2, 'ACTIVE'),
  ('00000000-0000-0004-0000-000000000007', 'Ngoại ngữ',                       'ngoai-ngu',                   NULL, '00000000-0000-0000-0000-000000000004', 2, 'ACTIVE'),
  ('00000000-0000-0004-0000-000000000008', 'Kỹ năng sống',                    'ky-nang-song',                NULL, '00000000-0000-0000-0000-000000000004', 2, 'ACTIVE'),
  ('00000000-0000-0004-0000-000000000009', 'Truyện tranh',                    'truyen-tranh',                NULL, '00000000-0000-0000-0000-000000000004', 2, 'ACTIVE'),
  ('00000000-0000-0004-0000-000000000010', 'Sách thiếu nhi',                  'sach-thieu-nhi',              NULL, '00000000-0000-0000-0000-000000000004', 2, 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

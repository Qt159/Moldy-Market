-- Manual migration: this repository does not install a Flyway runner.
-- Apply on a restored copy first; keep AWS publishers/cleanup and disputes disabled during rollout.
BEGIN;

ALTER TABLE vouchers ADD COLUMN IF NOT EXISTS issuer varchar(20) NOT NULL DEFAULT 'PLATFORM';
ALTER TABLE vouchers ADD COLUMN IF NOT EXISTS store_id uuid;
ALTER TABLE vouchers ADD COLUMN IF NOT EXISTS reserved_quantity integer NOT NULL DEFAULT 0;
ALTER TABLE vouchers ADD COLUMN IF NOT EXISTS minimum_order_value numeric(19,2);
ALTER TABLE vouchers ADD COLUMN IF NOT EXISTS maximum_discount numeric(19,2);
CREATE INDEX IF NOT EXISTS idx_voucher_store ON vouchers(store_id);

ALTER TABLE stored_files ADD COLUMN IF NOT EXISTS deletion_requested_at timestamp;
ALTER TABLE stored_files ADD COLUMN IF NOT EXISTS deletion_not_before timestamp;
ALTER TABLE stored_files ADD COLUMN IF NOT EXISTS deletion_attempt_count integer NOT NULL DEFAULT 0;
ALTER TABLE stored_files ADD COLUMN IF NOT EXISTS last_deletion_attempt_at timestamp;
ALTER TABLE stored_files ADD COLUMN IF NOT EXISTS deletion_claim_token uuid;
ALTER TABLE stored_files ADD COLUMN IF NOT EXISTS deletion_lease_until timestamp;
ALTER TABLE stored_files DROP CONSTRAINT IF EXISTS stored_files_status_check;
ALTER TABLE stored_files ADD CONSTRAINT stored_files_status_check
    CHECK (status IN ('PENDING','AVAILABLE','REJECTED','DELETE_PENDING','DELETED'));
CREATE INDEX IF NOT EXISTS idx_stored_files_status_created ON stored_files(status,created_at);

CREATE TABLE IF NOT EXISTS wishlist_items (
    id uuid PRIMARY KEY, user_id uuid NOT NULL, product_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT uk_wishlist_user_product UNIQUE(user_id,product_id)
);
CREATE INDEX IF NOT EXISTS idx_wishlist_user_created ON wishlist_items(user_id,created_at DESC,id DESC);
CREATE TABLE IF NOT EXISTS product_price_history (
    id uuid PRIMARY KEY, product_id uuid NOT NULL,
    old_price numeric(14,2) NOT NULL, new_price numeric(14,2) NOT NULL,
    changed_at timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_price_history_product_changed_at ON product_price_history(product_id,changed_at);

CREATE TABLE IF NOT EXISTS outbox_events (
    id uuid PRIMARY KEY, notification_id varchar(255) NOT NULL UNIQUE,
    aggregate_type varchar(100) NOT NULL, aggregate_id varchar(255) NOT NULL,
    event_type varchar(100) NOT NULL, payload jsonb NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'PENDING', retry_count integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, published_at timestamptz,
    locked_until timestamptz, lock_token uuid, last_error varchar(1000)
);
CREATE INDEX IF NOT EXISTS idx_outbox_pending ON outbox_events(status,next_attempt_at,created_at,id);

-- Preserve legacy columns and evidence. Populate participant IDs from their existing order.
ALTER TABLE dispute_requests ADD COLUMN IF NOT EXISTS buyer_id uuid;
ALTER TABLE dispute_requests ADD COLUMN IF NOT EXISTS seller_id uuid;
ALTER TABLE dispute_requests ADD COLUMN IF NOT EXISTS reason varchar(50) NOT NULL DEFAULT 'OTHER';
ALTER TABLE dispute_requests ADD COLUMN IF NOT EXISTS status varchar(50) NOT NULL DEFAULT 'OPEN';
ALTER TABLE dispute_requests ADD COLUMN IF NOT EXISTS reviewed_by uuid;
ALTER TABLE dispute_requests ADD COLUMN IF NOT EXISTS reviewed_at timestamptz;
ALTER TABLE dispute_requests ADD COLUMN IF NOT EXISTS decision_note varchar(1000);
ALTER TABLE dispute_requests ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE dispute_requests ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;
ALTER TABLE dispute_requests ALTER COLUMN description TYPE varchar(2000);
UPDATE dispute_requests d SET buyer_id=o.buyer_id, seller_id=o.seller_id
    FROM orders o WHERE o.id=d.order_id AND (d.buyer_id IS NULL OR d.seller_id IS NULL);
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM dispute_requests WHERE buyer_id IS NULL OR seller_id IS NULL) THEN
        RAISE EXCEPTION 'Disputes with missing participants require manual reconciliation';
    END IF;
    IF EXISTS (SELECT order_id FROM dispute_requests GROUP BY order_id HAVING count(*)>1) THEN
        RAISE EXCEPTION 'Duplicate legacy disputes require manual reconciliation; no rows were deleted';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema()
               AND table_name='dispute_requests' AND column_name='user_request_id') THEN
        ALTER TABLE dispute_requests ALTER COLUMN user_request_id DROP NOT NULL;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema()
               AND table_name='dispute_requests' AND column_name='images_url') THEN
        ALTER TABLE dispute_requests ALTER COLUMN images_url DROP NOT NULL;
    END IF;
END $$;
ALTER TABLE dispute_requests ALTER COLUMN buyer_id SET NOT NULL;
ALTER TABLE dispute_requests ALTER COLUMN seller_id SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_dispute_order ON dispute_requests(order_id);
CREATE TABLE IF NOT EXISTS dispute_images (
    dispute_id uuid NOT NULL REFERENCES dispute_requests(id), image_url varchar(1000) NOT NULL
);

-- Existing orders store enum ordinals. Do not convert to strings or renumber enum constants.
CREATE INDEX IF NOT EXISTS idx_orders_seller_created_at ON orders(seller_id,created_at);
CREATE INDEX IF NOT EXISTS idx_orders_seller_status_completed_at ON orders(seller_id,order_status,completed_at);
CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items(order_id);
COMMIT;

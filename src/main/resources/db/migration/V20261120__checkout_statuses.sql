-- Existing enum ordinals are preserved; new values are appended only.
ALTER TABLE public.orders DROP CONSTRAINT IF EXISTS orders_order_status_check;
ALTER TABLE public.orders ADD CONSTRAINT orders_order_status_check
    CHECK (order_status BETWEEN 0 AND 9);
ALTER TABLE public.checkout_groups DROP CONSTRAINT IF EXISTS checkout_groups_status_check;
ALTER TABLE public.checkout_groups ADD CONSTRAINT checkout_groups_status_check
    CHECK (status BETWEEN 0 AND 4);
CREATE INDEX IF NOT EXISTS idx_payments_status_expires ON public.payments (status, expired_at);

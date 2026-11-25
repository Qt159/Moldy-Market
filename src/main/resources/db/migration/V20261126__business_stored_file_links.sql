-- Manual migration. Adds additive media links; existing URL data is retained.
BEGIN;

ALTER TABLE stored_files ADD COLUMN IF NOT EXISTS linked_at timestamp;

CREATE TABLE IF NOT EXISTS product_media_files (
    product_id uuid NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    file_id uuid NOT NULL REFERENCES stored_files(id),
    media_order integer NOT NULL,
    CONSTRAINT pk_product_media_files PRIMARY KEY (product_id, media_order),
    CONSTRAINT uq_product_media_file UNIQUE (file_id)
);
CREATE INDEX IF NOT EXISTS idx_product_media_files_product ON product_media_files(product_id, media_order);

CREATE TABLE IF NOT EXISTS appraisal_report_media_files (
    report_id uuid NOT NULL REFERENCES appraisal_reports(id) ON DELETE CASCADE,
    file_id uuid NOT NULL REFERENCES stored_files(id),
    media_order integer NOT NULL,
    CONSTRAINT pk_appraisal_report_media_files PRIMARY KEY (report_id, media_order),
    CONSTRAINT uq_appraisal_report_media_file UNIQUE (file_id)
);
CREATE INDEX IF NOT EXISTS idx_appraisal_report_media_files_report
    ON appraisal_report_media_files(report_id, media_order);

CREATE TABLE IF NOT EXISTS dispute_media_files (
    dispute_id uuid NOT NULL REFERENCES dispute_requests(id) ON DELETE CASCADE,
    file_id uuid NOT NULL REFERENCES stored_files(id),
    media_order integer NOT NULL,
    CONSTRAINT pk_dispute_media_files PRIMARY KEY (dispute_id, media_order),
    CONSTRAINT uq_dispute_media_file UNIQUE (file_id)
);
CREATE INDEX IF NOT EXISTS idx_dispute_media_files_dispute
    ON dispute_media_files(dispute_id, media_order);

COMMIT;

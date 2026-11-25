-- Manual migration: this repository does not install a Flyway runner.
-- Keep legacy avatar_url values intact; newly linked avatars use avatar_file_id.
BEGIN;
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_file_id uuid;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_users_avatar_file') THEN
        ALTER TABLE users ADD CONSTRAINT fk_users_avatar_file
            FOREIGN KEY (avatar_file_id) REFERENCES stored_files(id) ON DELETE SET NULL;
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_users_avatar_file_id ON users(avatar_file_id);
COMMIT;

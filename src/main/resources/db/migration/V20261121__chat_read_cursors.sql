-- Additive only. Local ddl-auto=update also creates these columns.
ALTER TABLE conversation ADD COLUMN IF NOT EXISTS last_read_message_id_participant1 BIGINT;
ALTER TABLE conversation ADD COLUMN IF NOT EXISTS last_read_message_id_participant2 BIGINT;

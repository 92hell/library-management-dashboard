-- Notifications keep their English template and its values, so each reader sees them in their own
-- language (English or Bahasa Indonesia). message stays the English text (emails, older rows).
ALTER TABLE notifications ADD COLUMN message_key VARCHAR(500) NULL;
ALTER TABLE notifications ADD COLUMN message_args VARCHAR(1000) NULL;

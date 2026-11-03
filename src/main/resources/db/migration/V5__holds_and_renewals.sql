-- Holds shelf and renewals.
-- READY: a copy is set aside for the member until ready_until; WAITLISTED: approved, waiting for a
-- copy (queue order = requested_at); EXPIRED: a READY hold that was not collected in time.
ALTER TABLE borrowed_books ADD COLUMN ready_until DATE NULL;
ALTER TABLE borrowed_books ADD COLUMN renewal_count INT NOT NULL DEFAULT 0;
CREATE INDEX idx_borrowed_books_queue ON borrowed_books (book_id, status, requested_at);

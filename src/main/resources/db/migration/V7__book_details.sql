-- Book details: ISBN (stored as ISBN-13, digits only) and a short description.
-- The cover image is derived from the ISBN (Open Library covers), so it is not stored.
ALTER TABLE books ADD COLUMN isbn VARCHAR(13) NULL;
ALTER TABLE books ADD COLUMN description VARCHAR(2000) NULL;
CREATE INDEX idx_books_isbn ON books (isbn);

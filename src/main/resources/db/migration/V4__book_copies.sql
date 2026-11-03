-- Physical copies: a book (title) has one or more copies, each with its own barcode.
-- Loans are for a specific copy; a pending request is for the title and gets a copy when lent.

CREATE TABLE book_copies (
    id             BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    book_id        BIGINT      NOT NULL,
    barcode        VARCHAR(32) NOT NULL,
    shelf_location VARCHAR(50) NULL,
    is_deleted     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP   NULL,
    CONSTRAINT uq_book_copies_barcode UNIQUE (barcode),
    CONSTRAINT fk_book_copies_book FOREIGN KEY (book_id) REFERENCES books (id)
);
CREATE INDEX idx_book_copies_book ON book_copies (book_id, is_deleted);

-- Every existing book becomes one copy (deleted books keep a deleted copy for their history).
INSERT INTO book_copies (book_id, barcode, is_deleted, created_at)
SELECT id, CONCAT('BC-', id), is_deleted, CURRENT_TIMESTAMP FROM books;

ALTER TABLE borrowed_books ADD COLUMN copy_id BIGINT NULL;
ALTER TABLE borrowed_books ADD CONSTRAINT fk_borrowed_books_copy FOREIGN KEY (copy_id) REFERENCES book_copies (id);
CREATE INDEX idx_borrowed_books_copy_status ON borrowed_books (copy_id, status, is_deleted);

-- Lent and returned loans point at their book's (only) copy; requests stay without a copy.
UPDATE borrowed_books
SET copy_id = (SELECT c.id FROM book_copies c WHERE c.book_id = borrowed_books.book_id)
WHERE status IN ('ACTIVE', 'RETURNED');

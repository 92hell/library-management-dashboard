-- Indexes for the list/search queries (soft-delete filter + common joins and sorts).
CREATE INDEX idx_authors_deleted_created ON authors (is_deleted, created_at);
CREATE INDEX idx_books_deleted_created ON books (is_deleted, created_at);
CREATE INDEX idx_members_deleted_created ON members (is_deleted, created_at);
CREATE INDEX idx_borrowed_books_book_open ON borrowed_books (book_id, is_deleted, return_date);
CREATE INDEX idx_borrowed_books_member_open ON borrowed_books (member_id, is_deleted, return_date);
CREATE INDEX idx_borrowed_books_borrow_date ON borrowed_books (borrow_date);

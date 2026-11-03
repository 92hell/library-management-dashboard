-- Online accounts, borrow requests with a status workflow, due dates and notifications.

-- Every member gets a library card ID (the customer's key). Walk-in members link an online
-- account to it later by registering with the ID plus the email or phone on file.
ALTER TABLE members ADD COLUMN library_card_id VARCHAR(20) NULL;
UPDATE members SET library_card_id = CONCAT('LIB-', id) WHERE library_card_id IS NULL;
ALTER TABLE members MODIFY library_card_id VARCHAR(20) NOT NULL;
CREATE UNIQUE INDEX uq_members_library_card_id ON members (library_card_id);
-- Room for international numbers such as +6281234567890 (E.164 allows 15 digits plus '+').
ALTER TABLE members MODIFY phone VARCHAR(20) NOT NULL;

-- Staff (LIBRARIAN) and customer (CUSTOMER) logins. A customer account points at its member record.
CREATE TABLE user_accounts (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    display_name  VARCHAR(255) NOT NULL,
    email         VARCHAR(255),
    member_id     BIGINT       NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NULL,
    CONSTRAINT uq_user_accounts_username UNIQUE (username),
    CONSTRAINT uq_user_accounts_member UNIQUE (member_id),
    CONSTRAINT fk_user_accounts_member FOREIGN KEY (member_id) REFERENCES members (id)
);

-- Loans get a lifecycle: PENDING (requested online) -> ACTIVE (lent) -> RETURNED,
-- or PENDING -> REJECTED / CANCELLED. A pending request has no borrow date yet.
ALTER TABLE borrowed_books ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE borrowed_books ADD COLUMN due_date DATE NULL;
ALTER TABLE borrowed_books ADD COLUMN requested_at TIMESTAMP NULL;
ALTER TABLE borrowed_books ADD COLUMN decided_by VARCHAR(50) NULL;
ALTER TABLE borrowed_books ADD COLUMN decision_note VARCHAR(500) NULL;
ALTER TABLE borrowed_books ADD COLUMN due_soon_notified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE borrowed_books ADD COLUMN overdue_notified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE borrowed_books MODIFY borrow_date DATE NULL;

UPDATE borrowed_books SET status = 'RETURNED' WHERE return_date IS NOT NULL;
UPDATE borrowed_books SET due_date = TIMESTAMPADD(DAY, 14, borrow_date) WHERE due_date IS NULL;
-- Existing loans were recorded before reminders existed; do not flood members with old notices.
UPDATE borrowed_books SET due_soon_notified = TRUE, overdue_notified = TRUE WHERE status <> 'ACTIVE' OR due_date < CURRENT_DATE;

CREATE INDEX idx_borrowed_books_status_due ON borrowed_books (status, is_deleted, due_date);

CREATE TABLE notifications (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    type       VARCHAR(40)  NOT NULL,
    message    VARCHAR(500) NOT NULL,
    link       VARCHAR(255),
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NULL,
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES user_accounts (id)
);
CREATE INDEX idx_notifications_user_read ON notifications (user_id, is_read, created_at);

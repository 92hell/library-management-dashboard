-- Loan history and receipts.
-- loan_events: one row per status change of a loan (who, when, the status after it, a note).
-- receipts: a numbered, signed snapshot of a loan and its history (see ReceiptService).
-- receipt_keys: the signing key when none is configured (public key is published).
CREATE TABLE loan_events (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    loan_id     BIGINT       NOT NULL,
    type        VARCHAR(32)  NOT NULL,
    status      VARCHAR(16)  NOT NULL,
    occurred_at TIMESTAMP    NOT NULL,
    actor       VARCHAR(50)  NOT NULL,
    detail      VARCHAR(255) NULL,
    CONSTRAINT fk_loan_events_loan FOREIGN KEY (loan_id) REFERENCES borrowed_books (id)
);
CREATE INDEX idx_loan_events_loan ON loan_events (loan_id, id);

-- Loans from before the history was kept start with one entry describing their state then.
INSERT INTO loan_events (loan_id, type, status, occurred_at, actor, detail)
SELECT id, 'IMPORTED', status, COALESCE(created_at, CURRENT_TIMESTAMP), 'system',
       'Recorded before the loan history was kept'
FROM borrowed_books;

CREATE TABLE receipt_keys (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    key_id      VARCHAR(40)  NOT NULL,
    public_key  VARCHAR(500) NOT NULL,
    private_key VARCHAR(500) NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    CONSTRAINT uq_receipt_keys_key_id UNIQUE (key_id)
);

CREATE TABLE receipts (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    number      VARCHAR(20)  NULL,
    loan_id     BIGINT       NOT NULL,
    issued_at   TIMESTAMP    NOT NULL,
    issued_by   VARCHAR(50)  NOT NULL,
    fingerprint VARCHAR(64)  NOT NULL,
    content     VARCHAR(12000) NOT NULL,
    sha256      VARCHAR(64)  NOT NULL,
    signature   VARCHAR(200) NOT NULL,
    key_id      VARCHAR(40)  NOT NULL,
    CONSTRAINT uq_receipts_number UNIQUE (number),
    CONSTRAINT fk_receipts_loan FOREIGN KEY (loan_id) REFERENCES borrowed_books (id)
);
CREATE INDEX idx_receipts_loan ON receipts (loan_id, id);

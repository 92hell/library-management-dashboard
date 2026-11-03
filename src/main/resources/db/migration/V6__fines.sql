-- Late fees. One fine per loan returned after its due date; amounts are whole currency units.
-- UNPAID until a librarian records the payment (PAID) or waives it with a reason (WAIVED).
CREATE TABLE fines (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    loan_id     BIGINT       NOT NULL,
    member_id   BIGINT       NOT NULL,
    amount      BIGINT       NOT NULL,
    days_late   INT          NOT NULL,
    status      VARCHAR(16)  NOT NULL,
    created_at  TIMESTAMP    NULL,
    settled_at  TIMESTAMP    NULL,
    settled_by  VARCHAR(50)  NULL,
    note        VARCHAR(255) NULL,
    CONSTRAINT uq_fines_loan UNIQUE (loan_id),
    CONSTRAINT fk_fines_loan FOREIGN KEY (loan_id) REFERENCES borrowed_books (id),
    CONSTRAINT fk_fines_member FOREIGN KEY (member_id) REFERENCES members (id)
);
CREATE INDEX idx_fines_member_status ON fines (member_id, status);
CREATE INDEX idx_fines_status ON fines (status, created_at);

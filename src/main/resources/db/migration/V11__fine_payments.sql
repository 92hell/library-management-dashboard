-- Late fees can be paid in parts. paid_amount is what has been paid so far (the rest is still due
-- while the fine is UNPAID); each payment is kept in fine_payments. Fines already paid in full
-- become one payment for their whole amount.
ALTER TABLE fines ADD COLUMN paid_amount BIGINT NOT NULL DEFAULT 0;
UPDATE fines SET paid_amount = amount WHERE status = 'PAID';

CREATE TABLE fine_payments (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    fine_id     BIGINT      NOT NULL,
    amount      BIGINT      NOT NULL,
    paid_at     TIMESTAMP   NOT NULL,
    recorded_by VARCHAR(50) NOT NULL,
    CONSTRAINT fk_fine_payments_fine FOREIGN KEY (fine_id) REFERENCES fines (id)
);
CREATE INDEX idx_fine_payments_fine ON fine_payments (fine_id, id);
CREATE INDEX idx_fine_payments_paid_at ON fine_payments (paid_at);

INSERT INTO fine_payments (fine_id, amount, paid_at, recorded_by)
SELECT id, amount, COALESCE(settled_at, created_at, CURRENT_TIMESTAMP), COALESCE(settled_by, 'system')
FROM fines WHERE status = 'PAID';

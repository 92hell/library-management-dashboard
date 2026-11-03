-- A receipt belongs to the member it was issued to (its signed text names them), even if the loan's
-- member is corrected later. Existing receipts take the loan's current member.
ALTER TABLE receipts ADD COLUMN member_id BIGINT NULL;
UPDATE receipts SET member_id = (SELECT b.member_id FROM borrowed_books b WHERE b.id = receipts.loan_id);
ALTER TABLE receipts ADD CONSTRAINT fk_receipts_member FOREIGN KEY (member_id) REFERENCES members (id);

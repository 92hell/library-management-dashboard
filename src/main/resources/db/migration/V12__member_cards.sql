-- Library cards expire and are renewed at the desk. NULL = the card does not expire (cards issued
-- while the validity is set to 0). Cards that exist when this runs are valid for a year from now.
ALTER TABLE members ADD COLUMN card_expires_on DATE NULL;
UPDATE members SET card_expires_on = CURRENT_DATE + INTERVAL '1' YEAR;

-- Card IDs a member no longer uses: replaced (lost, damaged) or brought in by merging a duplicate
-- record. They still find the member, are never issued again, and cannot be used to register.
CREATE TABLE retired_cards (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    library_card_id VARCHAR(20)  NOT NULL,
    member_id       BIGINT       NOT NULL,
    reason          VARCHAR(20)  NOT NULL,
    note            VARCHAR(500) NULL,
    retired_at      TIMESTAMP    NOT NULL,
    retired_by      VARCHAR(50)  NOT NULL,
    CONSTRAINT uq_retired_cards_card UNIQUE (library_card_id),
    CONSTRAINT fk_retired_cards_member FOREIGN KEY (member_id) REFERENCES members (id)
);
CREATE INDEX idx_retired_cards_member ON retired_cards (member_id, id);

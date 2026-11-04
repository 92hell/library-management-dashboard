-- "Forgot password" links. Only a SHA-256 hash of the link's random token is kept, so the table
-- cannot be used to reset anyone's password. A token is deleted when it is used or replaced.
CREATE TABLE password_reset_tokens (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT      NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP   NOT NULL,
    created_at TIMESTAMP   NOT NULL,
    CONSTRAINT uq_password_reset_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_tokens_account FOREIGN KEY (account_id) REFERENCES user_accounts (id) ON DELETE CASCADE
);
CREATE INDEX idx_password_reset_tokens_account ON password_reset_tokens (account_id);

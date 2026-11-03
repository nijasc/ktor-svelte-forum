ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN password_changed_at TIMESTAMPTZ;

-- Accounts that exist now keep working. They can add an address from the account page.
UPDATE users SET email_verified_at = created_at;

CREATE TABLE email_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose VARCHAR(16) NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    email VARCHAR(254),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX email_tokens_user_idx ON email_tokens (user_id, purpose);

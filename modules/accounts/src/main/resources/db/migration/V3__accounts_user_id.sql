ALTER TABLE users ADD COLUMN id BIGSERIAL;
-- A plain unique index, so V5 can promote it to the primary key.
CREATE UNIQUE INDEX users_id_idx ON users (id);
ALTER TABLE users ADD COLUMN email VARCHAR(254);
ALTER TABLE users ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'member';
ALTER TABLE users ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'active';
ALTER TABLE users ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE UNIQUE INDEX users_email_idx ON users (lower(email)) WHERE email IS NOT NULL;

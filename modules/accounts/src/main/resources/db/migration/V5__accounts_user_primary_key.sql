-- Runs after V4, which removes the foreign keys that pointed at users (username) and adds ones that point at users (id).
-- Promote the unique index from V3 to the primary key, so those new foreign keys keep working.
ALTER TABLE users DROP CONSTRAINT users_pkey;
ALTER TABLE users ADD PRIMARY KEY USING INDEX users_id_idx;
CREATE UNIQUE INDEX users_username_idx ON users (lower(username));

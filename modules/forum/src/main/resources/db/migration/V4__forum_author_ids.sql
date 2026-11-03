ALTER TABLE threads ADD COLUMN author_id BIGINT REFERENCES users (id);
UPDATE threads SET author_id = users.id FROM users WHERE users.username = threads.author;
ALTER TABLE threads ALTER COLUMN author_id SET NOT NULL;
ALTER TABLE threads DROP COLUMN author;

ALTER TABLE posts ADD COLUMN author_id BIGINT REFERENCES users (id);
UPDATE posts SET author_id = users.id FROM users WHERE users.username = posts.author;
ALTER TABLE posts ALTER COLUMN author_id SET NOT NULL;
ALTER TABLE posts DROP COLUMN author;

CREATE INDEX threads_author_idx ON threads (author_id);
CREATE INDEX posts_author_idx ON posts (author_id);

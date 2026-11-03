CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(64) NOT NULL UNIQUE,
    title VARCHAR(120) NOT NULL,
    description VARCHAR(500) NOT NULL DEFAULT '',
    position INT NOT NULL DEFAULT 0
);

CREATE TABLE threads (
    id BIGSERIAL PRIMARY KEY,
    category_id BIGINT NOT NULL REFERENCES categories (id),
    title VARCHAR(200) NOT NULL,
    author VARCHAR(64) NOT NULL REFERENCES users (username),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX threads_category_idx ON threads (category_id, created_at DESC);

CREATE TABLE posts (
    id BIGSERIAL PRIMARY KEY,
    thread_id BIGINT NOT NULL REFERENCES threads (id) ON DELETE CASCADE,
    author VARCHAR(64) NOT NULL REFERENCES users (username),
    body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX posts_thread_idx ON posts (thread_id, created_at);

INSERT INTO categories (slug, title, description, position) VALUES
    ('general', 'General', 'Talk about anything.', 0),
    ('help', 'Help', 'Ask questions and get answers.', 1);

CREATE TABLE reports (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL REFERENCES posts (id),
    reporter_id BIGINT NOT NULL REFERENCES users (id),
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'open',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_by BIGINT REFERENCES users (id),
    resolved_at TIMESTAMPTZ,
    outcome VARCHAR(16),
    note TEXT NOT NULL DEFAULT '',
    UNIQUE (post_id, reporter_id)
);

CREATE INDEX reports_open_idx ON reports (status, id);

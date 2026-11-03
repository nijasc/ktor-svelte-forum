CREATE TABLE watches (
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    thread_id BIGINT NOT NULL REFERENCES threads (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, thread_id)
);

CREATE INDEX watches_thread_idx ON watches (thread_id);

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind VARCHAR(16) NOT NULL,
    actor_id BIGINT NOT NULL REFERENCES users (id),
    thread_id BIGINT NOT NULL REFERENCES threads (id) ON DELETE CASCADE,
    thread_title VARCHAR(200) NOT NULL,
    post_id BIGINT NOT NULL,
    count INT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at TIMESTAMPTZ
);

CREATE INDEX notifications_user_idx ON notifications (user_id, id DESC);
CREATE INDEX notifications_unread_idx ON notifications (user_id) WHERE read_at IS NULL;

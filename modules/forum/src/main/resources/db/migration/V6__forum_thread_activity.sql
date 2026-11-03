ALTER TABLE threads ADD COLUMN reply_count INT NOT NULL DEFAULT 0;
ALTER TABLE threads ADD COLUMN last_post_at TIMESTAMPTZ;

UPDATE threads
SET reply_count = GREATEST((SELECT count(*) FROM posts WHERE posts.thread_id = threads.id) - 1, 0),
    last_post_at = COALESCE((SELECT max(created_at) FROM posts WHERE posts.thread_id = threads.id), threads.created_at);

ALTER TABLE threads ALTER COLUMN last_post_at SET NOT NULL;
ALTER TABLE threads ALTER COLUMN last_post_at SET DEFAULT now();

DROP INDEX threads_category_idx;
CREATE INDEX threads_category_activity_idx ON threads (category_id, last_post_at DESC, id DESC);

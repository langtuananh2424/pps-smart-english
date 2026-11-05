CREATE TABLE lesson_progress (
    id               bigserial PRIMARY KEY,
    user_id          bigint  NOT NULL REFERENCES users (id),
    lesson_id        bigint  NOT NULL REFERENCES lessons (id),
    watched_sec      integer NOT NULL DEFAULT 0 CHECK (watched_sec >= 0),
    max_position_sec integer NOT NULL DEFAULT 0 CHECK (max_position_sec >= 0),
    completed_at     timestamp with time zone,
    created_at       timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_lesson_progress_user_lesson UNIQUE (user_id, lesson_id)
);
CREATE INDEX idx_lesson_progress_lesson ON lesson_progress (lesson_id);

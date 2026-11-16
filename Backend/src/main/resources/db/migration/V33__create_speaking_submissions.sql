-- FR-AI-03: phiên chấm nói. Audio nằm ở MinIO (object_key do server sinh), kết quả giữ lại khi audio bị xóa.
CREATE TABLE speaking_submissions (
    id                bigserial PRIMARY KEY,
    user_id           bigint       NOT NULL REFERENCES users (id),
    question_id       bigint       NOT NULL REFERENCES questions (id),
    course_id         bigint,
    status            varchar(20)  NOT NULL DEFAULT 'AWAITING_UPLOAD'
        CHECK (status IN ('AWAITING_UPLOAD', 'UPLOADED', 'GRADING', 'GRADED', 'UNCLEAR', 'FAILED', 'EXPIRED')),
    object_key        varchar(200) NOT NULL,
    max_seconds       integer      NOT NULL,
    max_bytes         bigint       NOT NULL,
    size_bytes        bigint,
    duration_sec      numeric(8, 2),
    upload_expires_at timestamp with time zone NOT NULL,
    job_id            bigint REFERENCES ai_jobs (id),
    result            json,
    audio_deleted_at  timestamp with time zone,
    created_at        timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_speaking_submissions_key ON speaking_submissions (object_key);
CREATE INDEX idx_speaking_submissions_user ON speaking_submissions (user_id, id DESC);
CREATE INDEX idx_speaking_submissions_status ON speaking_submissions (status, upload_expires_at);

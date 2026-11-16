-- Chấm AI: hàng đợi job, token usage, lượt định kỳ, trọng số tác vụ, chấm lại bài cũ (FR-ADM-02) và phiên chấm nói (FR-AI-03).
-- Cột JSON dùng kiểu json (chạy được cả H2 lẫn PostgreSQL); db/migration-pg/V100 đổi sang jsonb trên PostgreSQL.
CREATE TABLE writing_sessions (
    id           bigserial PRIMARY KEY,
    user_id      bigint      NOT NULL REFERENCES users (id),
    question_id  bigint      NOT NULL REFERENCES questions (id),
    step         integer     NOT NULL DEFAULT 0 CHECK (step >= 0), -- số câu hỏi đào sâu AI đã hỏi
    turns        json        NOT NULL,                              -- [{role, text}]
    draft        text,
    status       varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'READY', 'SUBMITTED')),
    grade_job_id bigint,
    created_at   timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_writing_sessions_user ON writing_sessions (user_id);

-- Hàng đợi AI nằm trong PostgreSQL (SKIP LOCKED), cùng transaction với việc trừ lượt.
-- Job chấm lại vẫn là REWRITE_GRADE, phân biệt bằng regrade_item_id.
-- course_id, period_start, charged_periodic, charged_pool: nguồn đã trừ, để hoàn đúng chỗ (D-31/D-32).
CREATE TABLE ai_jobs (
    id                bigserial PRIMARY KEY,
    user_id           bigint      NOT NULL REFERENCES users (id),
    kind              varchar(30) NOT NULL,
    status            varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED')),
    input             json,
    output            json,
    error             text,
    attempts          integer     NOT NULL DEFAULT 0,
    run_after         timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_until      timestamp with time zone,
    session_id        bigint REFERENCES writing_sessions (id),
    attempt_id        bigint REFERENCES student_exercise_attempts (id),
    charged           boolean     NOT NULL DEFAULT FALSE, -- đã trừ lượt: lỗi hẳn thì phải hoàn
    created_at        timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    regrade_item_id   bigint,
    course_id         bigint,
    period_start      date,
    charged_periodic  integer     NOT NULL DEFAULT 0,
    charged_pool      integer     NOT NULL DEFAULT 0,
    CONSTRAINT ck_ai_jobs_kind CHECK (kind IN
        ('WRITING_GRADE', 'SCAFFOLD_FOLLOWUP', 'SCAFFOLD_DRAFT', 'REWRITE_GRADE', 'SPEAKING_GRADE'))
);
CREATE INDEX idx_ai_jobs_status_run_after ON ai_jobs (status, run_after);
CREATE INDEX idx_ai_jobs_user_created ON ai_jobs (user_id, created_at DESC);
CREATE INDEX idx_ai_jobs_attempt ON ai_jobs (attempt_id);

ALTER TABLE writing_sessions ADD CONSTRAINT fk_writing_sessions_grade_job FOREIGN KEY (grade_job_id) REFERENCES ai_jobs (id);

CREATE TABLE ai_grading_token_usage (
    id                 bigserial PRIMARY KEY,
    job_id             bigint       NOT NULL REFERENCES ai_jobs (id),
    user_id            bigint       NOT NULL REFERENCES users (id),
    provider           varchar(30)  NOT NULL,
    model              varchar(100) NOT NULL,
    input_tokens       integer      NOT NULL DEFAULT 0,
    output_tokens      integer      NOT NULL DEFAULT 0,
    cache_read_tokens  integer      NOT NULL DEFAULT 0,
    cache_write_tokens integer      NOT NULL DEFAULT 0,
    audio_seconds      numeric(10, 2),
    cost_usd           numeric(12, 6) NOT NULL DEFAULT 0,
    created_at         timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_token_usage_job ON ai_grading_token_usage (job_id);
-- Báo cáo Admin (FR-ADM-03) lọc theo khoảng thời gian
CREATE INDEX idx_token_usage_created_at ON ai_grading_token_usage (created_at);

-- Đã dùng trong kỳ; còn lại = quota của khóa - used. Kỳ mới là dòng mới (period_start = ngày đầu kỳ, giờ VN).
CREATE TABLE ai_period_usage (
    id           bigserial PRIMARY KEY,
    user_id      bigint  NOT NULL REFERENCES users (id),
    course_id    bigint  NOT NULL REFERENCES courses (id),
    period_start date    NOT NULL,
    used         integer NOT NULL DEFAULT 0 CHECK (used >= 0),
    CONSTRAINT uq_ai_period_usage UNIQUE (user_id, course_id, period_start)
);

-- Trọng số trừ lượt (D-32), quản trị chỉnh được
CREATE TABLE ai_task_weights (
    code   varchar(30) PRIMARY KEY,
    weight integer NOT NULL CHECK (weight > 0)
);
INSERT INTO ai_task_weights (code, weight) VALUES
    ('REWRITE_SET', 1), ('WRITING_TASK1', 2), ('WRITING_TASK2', 3), ('SPEAKING_PART', 2);

-- Một lần chấm lại cho một phiên bản đáp án của một câu
CREATE TABLE regrade_runs (
    id               bigserial PRIMARY KEY,
    question_id      bigint      NOT NULL REFERENCES questions (id),
    question_version integer     NOT NULL,
    status           varchar(12) NOT NULL DEFAULT 'RUNNING' CHECK (status IN ('RUNNING', 'DONE', 'SUPERSEDED')),
    requested_by     bigint      NOT NULL REFERENCES users (id),
    total            integer     NOT NULL DEFAULT 0,
    changed          integer     NOT NULL DEFAULT 0,
    failed           integer     NOT NULL DEFAULT 0,
    created_at       timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at      timestamp with time zone,
    CONSTRAINT uq_regrade_runs_question_version UNIQUE (question_id, question_version)
);
CREATE INDEX idx_regrade_runs_status ON regrade_runs (status, id);

-- Vừa là hàng đợi của worker vừa là lịch sử điểm cũ (chỉ Admin xem)
CREATE TABLE regrade_items (
    id                bigserial PRIMARY KEY,
    run_id            bigint      NOT NULL REFERENCES regrade_runs (id),
    answer_id         bigint      NOT NULL REFERENCES student_answers (id),
    attempt_id        bigint      NOT NULL REFERENCES student_exercise_attempts (id),
    status            varchar(12) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'AI_WAITING', 'DONE', 'FAILED', 'CANCELLED')),
    job_id            bigint REFERENCES ai_jobs (id),
    old_is_correct    boolean,
    new_is_correct    boolean,
    old_attempt_score numeric(8, 2),
    new_attempt_score numeric(8, 2),
    old_result        json, -- student_answer_grading cũ của câu trả lời, trước khi bị thay
    new_result        json,
    processed_at      timestamp with time zone,
    CONSTRAINT uq_regrade_items_run_answer UNIQUE (run_id, answer_id)
);
CREATE INDEX idx_regrade_items_status ON regrade_items (status, id);
CREATE INDEX idx_regrade_items_run ON regrade_items (run_id, id);

ALTER TABLE ai_jobs ADD CONSTRAINT fk_ai_jobs_regrade_item FOREIGN KEY (regrade_item_id) REFERENCES regrade_items (id);
CREATE INDEX idx_ai_jobs_regrade_item ON ai_jobs (regrade_item_id);

-- Phiên chấm nói: audio nằm ở MinIO (object_key do server sinh), kết quả giữ lại khi audio bị xóa
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

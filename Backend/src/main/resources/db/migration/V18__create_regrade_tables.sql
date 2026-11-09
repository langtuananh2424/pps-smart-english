-- Chấm lại bài cũ khi Admin sửa đáp án (FR-ADM-02). Cột JSON dùng kiểu json (chạy được cả H2 lẫn PostgreSQL);
-- db/migration-pg/V18 đổi sang jsonb trên PostgreSQL.

-- Tăng mỗi lần payload, answer_key hoặc lựa chọn của câu thật sự đổi (trừ dạng WRITING)
ALTER TABLE questions ADD COLUMN answer_version integer NOT NULL DEFAULT 1;

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
    old_attempt_score integer,
    new_attempt_score integer,
    old_result        json, -- student_answer_grading cũ của câu trả lời, trước khi bị thay
    new_result        json,
    processed_at      timestamp with time zone,
    CONSTRAINT uq_regrade_items_run_answer UNIQUE (run_id, answer_id)
);
CREATE INDEX idx_regrade_items_status ON regrade_items (status, id);
CREATE INDEX idx_regrade_items_run ON regrade_items (run_id, id);

-- Job chấm lại vẫn là REWRITE_GRADE (CHECK của kind không đổi), phân biệt bằng cột này
ALTER TABLE ai_jobs ADD COLUMN regrade_item_id bigint;
ALTER TABLE ai_jobs ADD CONSTRAINT fk_ai_jobs_regrade_item FOREIGN KEY (regrade_item_id) REFERENCES regrade_items (id);
CREATE INDEX idx_ai_jobs_regrade_item ON ai_jobs (regrade_item_id);

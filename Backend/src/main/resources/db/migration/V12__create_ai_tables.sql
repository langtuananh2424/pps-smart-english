-- Cột JSON dùng kiểu json (chạy được cả H2 lẫn PostgreSQL); db/migration-pg/V13 đổi sang jsonb trên PostgreSQL.
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

-- Hàng đợi AI nằm trong PostgreSQL (SKIP LOCKED), cùng transaction với việc trừ lượt
CREATE TABLE ai_jobs (
    id           bigserial PRIMARY KEY,
    user_id      bigint      NOT NULL REFERENCES users (id),
    kind         varchar(30) NOT NULL CHECK (kind IN ('WRITING_GRADE', 'SCAFFOLD_FOLLOWUP', 'SCAFFOLD_DRAFT', 'REWRITE_GRADE')),
    status       varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED')),
    input        json,
    output       json,
    error        text,
    attempts     integer     NOT NULL DEFAULT 0,
    run_after    timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_until timestamp with time zone,
    session_id   bigint REFERENCES writing_sessions (id),
    attempt_id   bigint REFERENCES student_exercise_attempts (id),
    charged      boolean     NOT NULL DEFAULT FALSE, -- đã trừ lượt: lỗi hẳn thì phải hoàn
    created_at   timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_ai_jobs_status_run_after ON ai_jobs (status, run_after);
CREATE INDEX idx_ai_jobs_user_created ON ai_jobs (user_id, created_at DESC);
CREATE INDEX idx_ai_jobs_attempt ON ai_jobs (attempt_id);

ALTER TABLE writing_sessions ADD CONSTRAINT fk_writing_sessions_grade_job FOREIGN KEY (grade_job_id) REFERENCES ai_jobs (id);

CREATE TABLE ai_grading_token_usage (
    id                bigserial PRIMARY KEY,
    job_id            bigint       NOT NULL REFERENCES ai_jobs (id),
    user_id           bigint       NOT NULL REFERENCES users (id),
    provider          varchar(30)  NOT NULL,
    model             varchar(100) NOT NULL,
    input_tokens      integer      NOT NULL DEFAULT 0,
    output_tokens     integer      NOT NULL DEFAULT 0,
    cache_read_tokens integer      NOT NULL DEFAULT 0,
    cache_write_tokens integer     NOT NULL DEFAULT 0,
    audio_seconds     numeric(10, 2),
    cost_usd          numeric(12, 6) NOT NULL DEFAULT 0,
    created_at        timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_token_usage_job ON ai_grading_token_usage (job_id);

-- Kết quả chấm từng câu trả lời: RULE (luật, miễn phí) hoặc AI
CREATE TABLE student_answer_grading (
    id         bigserial PRIMARY KEY,
    answer_id  bigint      NOT NULL REFERENCES student_answers (id),
    grader     varchar(10) NOT NULL CHECK (grader IN ('RULE', 'AI')),
    score      integer     NOT NULL,
    result     json,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_answer_grading_answer_grader UNIQUE (answer_id, grader)
);

-- Mỗi (lý do, tham chiếu) chỉ một dòng sổ cái: PURCHASE một lần mỗi đơn, GRADING và REFUND một lần mỗi job.
-- Lưới an toàn thứ hai chống hoàn lượt hai lần (lưới thứ nhất là chuyển trạng thái job có điều kiện).
CREATE UNIQUE INDEX uq_ai_quota_ledger_reason_ref ON ai_quota_ledger (reason, ref_id);

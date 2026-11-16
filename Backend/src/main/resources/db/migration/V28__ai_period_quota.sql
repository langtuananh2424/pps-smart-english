-- FR-AI-04, FR-COM-05, D-31/D-32/D-47: lượt AI định kỳ theo khóa + pool mua thêm + trọng số tác vụ.

-- Cấu hình lượt định kỳ của khóa (D-21: mặc định 20 lượt/tuần)
ALTER TABLE courses ADD COLUMN ai_period varchar(10) NOT NULL DEFAULT 'WEEK';
ALTER TABLE courses ADD COLUMN ai_period_quota integer NOT NULL DEFAULT 20;
ALTER TABLE courses ADD CONSTRAINT ck_courses_ai_period CHECK (ai_period IN ('WEEK', 'MONTH'));
ALTER TABLE courses ADD CONSTRAINT ck_courses_ai_period_quota CHECK (ai_period_quota >= 0);

-- Đã dùng trong kỳ; còn lại = quota của khóa - used. Không cần job nạp lại: kỳ mới là dòng mới (period_start = ngày đầu kỳ, giờ VN).
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

-- Job lưu nguồn đã trừ để hoàn đúng chỗ. Job cũ (đã trừ 1 lượt từ số dư) có hai cột = 0: hoàn 1 lượt vào pool.
ALTER TABLE ai_jobs ADD COLUMN course_id bigint;
ALTER TABLE ai_jobs ADD COLUMN period_start date;
ALTER TABLE ai_jobs ADD COLUMN charged_periodic integer NOT NULL DEFAULT 0;
ALTER TABLE ai_jobs ADD COLUMN charged_pool integer NOT NULL DEFAULT 0;

-- Sổ cái ghi nguồn; một job có thể ghi hai dòng (PERIODIC + POOL) nên khóa duy nhất thêm source.
ALTER TABLE ai_quota_ledger ADD COLUMN source varchar(10) NOT NULL DEFAULT 'POOL';
ALTER TABLE ai_quota_ledger ADD COLUMN course_id bigint;
ALTER TABLE ai_quota_ledger ADD CONSTRAINT ck_ai_quota_ledger_source CHECK (source IN ('PERIODIC', 'POOL'));
DROP INDEX uq_ai_quota_ledger_reason_ref;
CREATE UNIQUE INDEX uq_ai_quota_ledger_reason_ref_source ON ai_quota_ledger (reason, ref_id, source);

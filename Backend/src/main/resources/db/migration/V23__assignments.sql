-- FR-ASG-01..04, D-112, D-113, D-121, D-130: giao bài cho lớp, người nhận, điểm và nhận xét của giáo viên.
-- Chỉ thêm bảng và cột có thể null/mặc định nên bản chạy cũ vẫn tương thích.
CREATE TABLE assignments (
    id          bigserial PRIMARY KEY,
    class_id    bigint      NOT NULL REFERENCES classes (id),
    bank_id     bigint      NOT NULL REFERENCES question_banks (id),
    due_at      timestamp with time zone NOT NULL,
    status      varchar(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'CANCELLED')),
    -- 'lớp:bộ câu hỏi:hạn' khi ACTIVE, NULL khi hủy: unique chặn giao trùng cùng bài, lớp, hạn kể cả khi đua nhau
    live_key    varchar(80),
    reminded_at timestamp with time zone, -- đã nhắc hạn
    created_by  bigint      NOT NULL REFERENCES users (id),
    created_at  timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_assignments_live UNIQUE (live_key)
);
CREATE INDEX idx_assignments_class ON assignments (class_id, id DESC);
CREATE INDEX idx_assignments_due ON assignments (status, due_at);
CREATE INDEX idx_assignments_bank ON assignments (bank_id);

-- Không có dòng nào cho một bài = giao cho cả lớp (kể cả học viên vào lớp sau); có dòng = chỉ những người đó.
CREATE TABLE assignment_targets (
    id            bigserial PRIMARY KEY,
    assignment_id bigint NOT NULL REFERENCES assignments (id),
    user_id       bigint NOT NULL REFERENCES users (id),
    CONSTRAINT uq_assignment_targets UNIQUE (assignment_id, user_id)
);

-- Mỗi lần giáo viên sửa điểm hoặc nhận xét là một dòng mới, không ghi đè (D-113). Điểm cuối là dòng mới nhất có điểm.
CREATE TABLE grade_overrides (
    id         bigserial PRIMARY KEY,
    attempt_id bigint NOT NULL REFERENCES student_exercise_attempts (id),
    score      numeric(8, 2) CHECK (score IS NULL OR score >= 0),
    comment    text,
    graded_by  bigint NOT NULL REFERENCES users (id),
    graded_at  timestamp with time zone NOT NULL,
    CONSTRAINT ck_grade_overrides_content CHECK (score IS NOT NULL OR comment IS NOT NULL)
);
CREATE INDEX idx_grade_overrides_attempt ON grade_overrides (attempt_id, id);

-- Lượt làm của bài được giao; null với bài thường. Trễ hạn suy ra từ submitted_at và assignments.due_at, không lưu cờ (D-121).
ALTER TABLE student_exercise_attempts ADD COLUMN assignment_id bigint REFERENCES assignments (id);
CREATE INDEX idx_attempts_assignment ON student_exercise_attempts (assignment_id, user_id);

-- Nhắc hạn nộp bài trước bao nhiêu giờ (D-124), mặc định 24.
ALTER TABLE class_settings ADD COLUMN assignment_reminder_hours integer NOT NULL DEFAULT 24
    CHECK (assignment_reminder_hours BETWEEN 1 AND 720);

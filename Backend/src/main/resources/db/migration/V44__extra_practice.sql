-- D-152 (FR-EX-07): Luyện thêm. Lượt gồm câu chọn từ nhiều bộ gắn vào một "bộ neo" (purpose EXTRA, không topic, luôn DRAFT)
-- nên không chạm tiến độ Topic/Unit (tiến độ tính theo bank_id của lượt) và mọi luồng nộp/chấm/nháp dùng lại nguyên.
-- Tương thích ngược: chỉ thêm cột có mặc định, bảng mới và nới CHECK; bản chạy cũ bỏ qua hàng EXTRA.
ALTER TABLE question_banks DROP CONSTRAINT ck_question_banks_purpose;
ALTER TABLE question_banks ADD CONSTRAINT ck_question_banks_purpose CHECK (
    (purpose = 'COURSE' AND topic_id IS NOT NULL)
    OR (purpose = 'PLACEMENT' AND topic_id IS NULL AND mode = 'EXAM' AND time_limit_sec IS NOT NULL)
    OR (purpose = 'EXTRA' AND topic_id IS NULL AND mode = 'PRACTICE'));
INSERT INTO question_banks (title, status, mode, purpose, is_trial) VALUES ('Luyện thêm', 'DRAFT', 'PRACTICE', 'EXTRA', FALSE);

ALTER TABLE student_exercise_attempts ADD COLUMN extra boolean NOT NULL DEFAULT FALSE;

-- Danh sách câu của lượt Luyện thêm (thứ tự hiển thị theo sort_order).
CREATE TABLE attempt_questions (
    attempt_id  bigint  NOT NULL REFERENCES student_exercise_attempts (id) ON DELETE CASCADE,
    question_id bigint  NOT NULL REFERENCES questions (id),
    sort_order  integer NOT NULL,
    PRIMARY KEY (attempt_id, question_id)
);
CREATE INDEX idx_attempt_questions_question ON attempt_questions (question_id);

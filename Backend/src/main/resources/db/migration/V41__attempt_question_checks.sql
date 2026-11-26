-- D-150 (FR-EX-04): mỗi lần học viên bấm "Kiểm tra" một câu trong lượt đang mở. Điểm vẫn chấm khi nộp; bảng này chỉ đếm
-- số lần kiểm tra sai để khóa câu khi hết lượt thử và để thống kê câu sai nhiều (FR-EX-08).
CREATE TABLE attempt_question_checks (
    id          bigserial PRIMARY KEY,
    attempt_id  bigint  NOT NULL REFERENCES student_exercise_attempts (id) ON DELETE CASCADE,
    question_id bigint  NOT NULL REFERENCES questions (id),
    wrong_count integer NOT NULL DEFAULT 0,
    solved      boolean NOT NULL DEFAULT false, -- đã có một lần kiểm tra đúng: câu khóa
    CONSTRAINT uq_attempt_question_checks UNIQUE (attempt_id, question_id)
);

-- FR-EX-08, FR-EX-09, D-137: lưu quá trình làm bài và thống kê hồ sơ năng lực, tính khi truy vấn (không bảng tổng hợp).
-- time_spent_ms: client báo, máy chủ đã chặn giá trị phi lý (null = không có số liệu).
-- answer_version: phiên bản đáp án của câu lúc chấm; dòng cũ coi là phiên bản 1 (tương thích ngược).
ALTER TABLE student_answers ADD COLUMN time_spent_ms integer;
ALTER TABLE student_answers ADD COLUMN answer_version integer NOT NULL DEFAULT 1;

CREATE INDEX idx_student_answers_question ON student_answers (question_id);
CREATE INDEX idx_attempts_user_submitted ON student_exercise_attempts (user_id, submitted_at);

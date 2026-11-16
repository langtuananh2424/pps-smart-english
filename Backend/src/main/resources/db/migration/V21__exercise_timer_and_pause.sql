-- D-11, FR-EX-06: giới hạn thời gian theo bộ bài tập; chế độ PRACTICE (được tạm dừng) hoặc EXAM (không tạm dừng).
ALTER TABLE question_banks ADD COLUMN time_limit_sec integer;
ALTER TABLE question_banks ADD COLUMN mode varchar(20) NOT NULL DEFAULT 'PRACTICE';
ALTER TABLE question_banks ADD CONSTRAINT ck_question_banks_mode CHECK (mode IN ('PRACTICE', 'EXAM'));

-- Đồng hồ của lượt làm: time_used_sec là thời gian đã chạy trước các lần tạm dừng; resumed_at là lúc bắt đầu đoạn đang chạy
-- (null khi đang tạm dừng). draft_answers: đáp án đã lưu, máy chủ nộp khi hết giờ.
ALTER TABLE student_exercise_attempts ADD COLUMN time_limit_sec integer;
ALTER TABLE student_exercise_attempts ADD COLUMN time_used_sec integer NOT NULL DEFAULT 0;
ALTER TABLE student_exercise_attempts ADD COLUMN resumed_at timestamp with time zone;
ALTER TABLE student_exercise_attempts ADD COLUMN draft_answers json;

-- D-151 (FR-EX-05): chế độ Luyện tập / Mô phỏng thi của bộ IELTS Reading/Listening do học viên chọn khi bắt đầu lượt.
-- Null = lượt cũ hoặc bộ ngoài phạm vi: theo question_banks.mode. Cột nullable để bản chạy cũ vẫn ghi lượt được.
ALTER TABLE student_exercise_attempts ADD COLUMN mode varchar(10) CHECK (mode IN ('PRACTICE', 'EXAM'));

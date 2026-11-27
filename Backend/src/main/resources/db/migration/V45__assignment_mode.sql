-- D-155 (FR-EX-05, FR-ASG-01): giáo viên chọn chế độ Luyện tập / Thi lúc giao bài. Null = theo question_banks.mode.
-- Cột nullable để bản chạy cũ vẫn tạo được bài giao.
ALTER TABLE assignments ADD COLUMN mode varchar(10) CHECK (mode IN ('PRACTICE', 'EXAM'));

-- FR-ASG-01..04, D-130: giao riêng câu Viết/Nói chấm AI. Chỉ thêm cột null được nên bản chạy cũ vẫn tương thích.
-- Bài giao câu hỏi vẫn giữ bank_id (bộ chứa câu) để mở riêng bài ở unit chưa mở như bài giao bộ câu hỏi.
ALTER TABLE assignments ADD COLUMN question_id bigint REFERENCES questions (id);
ALTER TABLE assignments ADD COLUMN question_type varchar(20); -- WRITING hoặc SPEAKING; null = giao cả bộ câu hỏi

-- Phiên viết và lượt nói làm trong ngữ cảnh bài giao; submitted_at = lúc nộp, để đánh dấu trễ hạn (D-121).
ALTER TABLE writing_sessions ADD COLUMN assignment_id bigint REFERENCES assignments (id);
ALTER TABLE writing_sessions ADD COLUMN submitted_at timestamp with time zone;
CREATE INDEX idx_writing_sessions_assignment ON writing_sessions (assignment_id, user_id);
ALTER TABLE speaking_submissions ADD COLUMN assignment_id bigint REFERENCES assignments (id);
ALTER TABLE speaking_submissions ADD COLUMN submitted_at timestamp with time zone;
CREATE INDEX idx_speaking_submissions_assignment ON speaking_submissions (assignment_id, user_id);

-- Điểm giáo viên sửa cho bài Viết/Nói gắn với phiên viết hoặc lượt nói thay vì lượt làm bài: đúng một trong ba.
ALTER TABLE grade_overrides ALTER COLUMN attempt_id DROP NOT NULL;
ALTER TABLE grade_overrides ADD COLUMN writing_session_id bigint REFERENCES writing_sessions (id);
ALTER TABLE grade_overrides ADD COLUMN speaking_submission_id bigint REFERENCES speaking_submissions (id);
CREATE INDEX idx_grade_overrides_writing ON grade_overrides (writing_session_id, id);
CREATE INDEX idx_grade_overrides_speaking ON grade_overrides (speaking_submission_id, id);
ALTER TABLE grade_overrides ADD CONSTRAINT ck_grade_overrides_subject CHECK (
    (CASE WHEN attempt_id IS NULL THEN 0 ELSE 1 END)
  + (CASE WHEN writing_session_id IS NULL THEN 0 ELSE 1 END)
  + (CASE WHEN speaking_submission_id IS NULL THEN 0 ELSE 1 END) = 1);

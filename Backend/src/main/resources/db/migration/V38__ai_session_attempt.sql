-- D-145: phiên Viết/Nói mở từ trong một lượt làm bài thì gắn lượt đó, để điểm AI về sau cộng vào điểm lượt.
-- Cột nullable: phiên cũ và phiên luyện tập giữ NULL, không ảnh hưởng gì.
ALTER TABLE writing_sessions ADD COLUMN attempt_id bigint REFERENCES student_exercise_attempts (id);
ALTER TABLE speaking_submissions ADD COLUMN attempt_id bigint REFERENCES student_exercise_attempts (id);
CREATE INDEX idx_writing_sessions_attempt ON writing_sessions (attempt_id);
CREATE INDEX idx_speaking_submissions_attempt ON speaking_submissions (attempt_id);

-- Lượt đã nộp trước D-145 không tính câu Viết/Nói vào maxScore; credit 0 giữ nguyên score và không để passed chờ mãi.
UPDATE student_answers SET credit = 0
WHERE credit IS NULL AND question_id IN (SELECT id FROM questions WHERE type IN ('WRITING', 'SPEAKING'));

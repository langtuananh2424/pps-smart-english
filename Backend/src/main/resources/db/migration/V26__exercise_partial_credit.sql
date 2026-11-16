-- D-44, FR-EX-02: chấm từng phần. credit là điểm câu 0..1 (4 chữ số thập phân), null khi câu chưa chấm (dạng AI).
ALTER TABLE student_answers ADD COLUMN credit numeric(5, 4);
UPDATE student_answers SET credit = 1 WHERE is_correct = TRUE;
UPDATE student_answers SET credit = 0 WHERE is_correct = FALSE;

-- Điểm lượt làm bài là tổng credit nên có thể lẻ; max_score vẫn là số câu (nguyên).
ALTER TABLE student_exercise_attempts ALTER COLUMN score SET DATA TYPE numeric(8, 2);
ALTER TABLE regrade_items ALTER COLUMN old_attempt_score SET DATA TYPE numeric(8, 2);
ALTER TABLE regrade_items ALTER COLUMN new_attempt_score SET DATA TYPE numeric(8, 2);

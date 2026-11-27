-- D-166: điểm giáo viên sửa thay điểm của lượt; machine_score giữ điểm máy chấm trước lần sửa đầu (null = chưa sửa).
ALTER TABLE student_exercise_attempts ADD COLUMN machine_score numeric(8, 2);

-- Lượt đã được sửa điểm trước D-166: score lúc đó vẫn là điểm máy nên chép sang machine_score, rồi áp điểm sửa mới nhất.
UPDATE student_exercise_attempts
SET machine_score = score
WHERE EXISTS (SELECT 1 FROM grade_overrides o WHERE o.attempt_id = student_exercise_attempts.id AND o.score IS NOT NULL);

UPDATE student_exercise_attempts
SET score = (SELECT o.score FROM grade_overrides o
             WHERE o.id = (SELECT max(o2.id) FROM grade_overrides o2
                           WHERE o2.attempt_id = student_exercise_attempts.id AND o2.score IS NOT NULL))
WHERE machine_score IS NOT NULL;

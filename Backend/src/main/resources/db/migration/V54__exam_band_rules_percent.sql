-- D-167 (FR-EXAM-03): bảng quy đổi theo % điểm của kỹ năng thay cho số câu đúng. min_correct giữ lại (nullable) để dữ liệu và bản chạy cũ đọc được.
ALTER TABLE exam_band_rules ADD COLUMN min_percent integer;
ALTER TABLE exam_band_rules ALTER COLUMN min_correct DROP NOT NULL;

-- Đổi số câu đúng sang % theo số câu còn hiệu lực của kỹ năng trong bộ, làm tròn xuống (phép chia số nguyên); tối đa 100.
UPDATE exam_band_rules
SET min_percent = CASE WHEN min_correct = 0 THEN 0
    ELSE LEAST(100, min_correct * 100 / GREATEST(1, (SELECT COUNT(*) FROM questions q
        WHERE q.bank_id = exam_band_rules.bank_id AND q.skill = exam_band_rules.skill AND q.archived_at IS NULL))) END;

-- Làm tròn xuống có thể làm hai dòng trùng mốc %: giữ dòng có số câu đúng cao hơn (band cao hơn).
DELETE FROM exam_band_rules
WHERE EXISTS (SELECT 1 FROM exam_band_rules o WHERE o.bank_id = exam_band_rules.bank_id AND o.skill = exam_band_rules.skill
    AND o.min_percent = exam_band_rules.min_percent AND o.min_correct > exam_band_rules.min_correct);

ALTER TABLE exam_band_rules ALTER COLUMN min_percent SET NOT NULL;
ALTER TABLE exam_band_rules ADD CONSTRAINT ck_exam_band_rules_percent CHECK (min_percent BETWEEN 0 AND 100);
ALTER TABLE exam_band_rules ADD CONSTRAINT uq_exam_band_rules_percent UNIQUE (bank_id, skill, min_percent);

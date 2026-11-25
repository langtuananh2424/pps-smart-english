-- #71 (D-145): ngưỡng Đạt/Chưa đạt của bài thường, theo từng bộ bài. Bộ cũ tự nhận 70, cột có mặc định nên bản chạy cũ vẫn tương thích.
ALTER TABLE question_banks ADD COLUMN pass_pct integer NOT NULL DEFAULT 70
    CONSTRAINT chk_question_banks_pass_pct CHECK (pass_pct BETWEEN 1 AND 100);

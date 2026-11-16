-- Chỉ PostgreSQL: db/migration/V21 tạo draft_answers kiểu json (H2 chạy được), ở đây nâng lên jsonb như các bảng khác.
ALTER TABLE student_exercise_attempts ALTER COLUMN draft_answers TYPE jsonb USING draft_answers::jsonb;

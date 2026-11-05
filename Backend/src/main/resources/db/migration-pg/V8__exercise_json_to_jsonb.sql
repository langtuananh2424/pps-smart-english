-- Chỉ PostgreSQL: H2 (test) không có jsonb nên V7 tạo json, ở đây nâng lên jsonb theo thiết kế.
ALTER TABLE questions ALTER COLUMN payload TYPE jsonb USING payload::jsonb;
ALTER TABLE questions ALTER COLUMN answer_key TYPE jsonb USING answer_key::jsonb;
ALTER TABLE student_answers ALTER COLUMN answer TYPE jsonb USING answer::jsonb;

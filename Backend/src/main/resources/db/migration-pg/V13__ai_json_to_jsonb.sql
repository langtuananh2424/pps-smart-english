-- Chỉ PostgreSQL: H2 (test) không có jsonb nên V12 tạo json, ở đây nâng lên jsonb theo thiết kế.
ALTER TABLE writing_sessions ALTER COLUMN turns TYPE jsonb USING turns::jsonb;
ALTER TABLE ai_jobs ALTER COLUMN input TYPE jsonb USING input::jsonb;
ALTER TABLE ai_jobs ALTER COLUMN output TYPE jsonb USING output::jsonb;
ALTER TABLE student_answer_grading ALTER COLUMN result TYPE jsonb USING result::jsonb;

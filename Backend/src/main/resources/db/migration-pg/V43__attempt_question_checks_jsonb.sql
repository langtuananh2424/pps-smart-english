-- Chỉ PostgreSQL: V41 tạo cột json cho H2; các cột JSON khác đã là jsonb từ V8.
ALTER TABLE attempt_question_checks ALTER COLUMN answer TYPE jsonb USING answer::jsonb;

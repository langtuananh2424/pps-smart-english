-- Chỉ PostgreSQL: H2 (test) không có jsonb nên V33 tạo json.
ALTER TABLE speaking_submissions ALTER COLUMN result TYPE jsonb USING result::jsonb;

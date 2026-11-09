-- Chỉ PostgreSQL: H2 (test) không có jsonb nên V18 tạo json, ở đây nâng lên jsonb theo thiết kế.
ALTER TABLE regrade_items ALTER COLUMN old_result TYPE jsonb USING old_result::jsonb;
ALTER TABLE regrade_items ALTER COLUMN new_result TYPE jsonb USING new_result::jsonb;

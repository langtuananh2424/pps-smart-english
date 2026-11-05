-- Chỉ PostgreSQL: H2 (test) không có jsonb nên V9 tạo json, ở đây nâng lên jsonb theo thiết kế.
ALTER TABLE payment_transactions ALTER COLUMN raw_payload TYPE jsonb USING raw_payload::jsonb;

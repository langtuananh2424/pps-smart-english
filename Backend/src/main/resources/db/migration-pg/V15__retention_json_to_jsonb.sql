-- Chỉ PostgreSQL: H2 (test) không có jsonb nên V14 tạo json, ở đây nâng lên jsonb theo thiết kế.
ALTER TABLE push_subscriptions ALTER COLUMN keys TYPE jsonb USING keys::jsonb;
ALTER TABLE notifications ALTER COLUMN payload TYPE jsonb USING payload::jsonb;

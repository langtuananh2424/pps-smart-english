-- Chỉ PostgreSQL: db/migration/V31 tạo cột json (H2 chạy được), ở đây nâng lên jsonb như các bảng khác.
ALTER TABLE placement_attempts ALTER COLUMN draft_answers TYPE jsonb USING draft_answers::jsonb;
ALTER TABLE placement_attempts ALTER COLUMN answers TYPE jsonb USING answers::jsonb;
ALTER TABLE placement_attempts ALTER COLUMN skill_results TYPE jsonb USING skill_results::jsonb;

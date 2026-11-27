-- Chỉ PostgreSQL: V47/V49 tạo cột json cho H2; đổi sang jsonb như các cột JSON khác (V8, V43).
ALTER TABLE question_groups ALTER COLUMN layout TYPE jsonb USING layout::jsonb;
ALTER TABLE attempt_video_groups ALTER COLUMN answers TYPE jsonb USING answers::jsonb;
ALTER TABLE attempt_video_groups ALTER COLUMN wrong_ids TYPE jsonb USING wrong_ids::jsonb;

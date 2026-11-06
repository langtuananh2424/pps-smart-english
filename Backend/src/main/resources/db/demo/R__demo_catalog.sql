-- DỮ LIỆU DEMO, KHÔNG CHẠY Ở PRODUCTION. Chỉ nạp khi bật location classpath:db/demo (DEMO_FLYWAY_LOCATION).
-- Idempotent: chạy lại không tạo trùng; khóa theo slug, (course, code) và sort_order.
DO $$
DECLARE
    c RECORD;
    cid bigint;
    lid bigint;
    tid bigint;
    lv int;
    tp int;
BEGIN
    FOR c IN
        SELECT * FROM (VALUES
            ('ielts-0-3',    'IELTS 0 → 3.0',  'Xây nền tảng từ con số 0, hướng tới band 3.0.',          'IELTS'),
            ('ielts-3-5',    'IELTS 3.0 → 5.0', 'Củng cố ngữ pháp và từ nối để đạt band 5.0.',           'IELTS'),
            ('ielts-5-7',    'IELTS 5.0 → 7.0', 'Nâng độ chính xác và độ tự nhiên để đạt band 7.0.',     'IELTS'),
            ('ielts-7-8',    'IELTS 7.0 → 8.0', 'Tinh chỉnh diễn đạt học thuật hướng tới band 8.0.',     'IELTS'),
            ('cambridge-demo', 'Cambridge cơ bản', 'Luyện nền tảng cho các chứng chỉ Cambridge.',         'CAMBRIDGE'),
            ('pho-thong-demo', 'Tiếng Anh phổ thông', 'Ôn luyện tiếng Anh theo chương trình phổ thông.',  'GENERAL'),
            ('giao-tiep-demo', 'Tiếng Anh giao tiếp', 'Phản xạ giao tiếp hằng ngày tự nhiên.',          'COMMUNICATION')
        ) AS v(slug, title, description, target_type)
    LOOP
        INSERT INTO courses (slug, title, description, target_type, status)
        VALUES (c.slug, c.title, c.description, c.target_type, 'PUBLISHED')
        ON CONFLICT (slug) DO UPDATE
            SET title = EXCLUDED.title, description = EXCLUDED.description,
                target_type = EXCLUDED.target_type, status = 'PUBLISHED', updated_at = CURRENT_TIMESTAMP
        RETURNING id INTO cid;

        FOR lv IN 1..2 LOOP
            INSERT INTO course_levels (course_id, code, title, sort_order, status)
            VALUES (cid, 'L' || lv, 'Cấp độ ' || lv, lv, 'PUBLISHED')
            ON CONFLICT (course_id, code) DO UPDATE
                SET title = EXCLUDED.title, sort_order = EXCLUDED.sort_order, status = 'PUBLISHED', updated_at = CURRENT_TIMESTAMP
            RETURNING id INTO lid;

            FOR tp IN 1..2 LOOP
                SELECT id INTO tid FROM topics WHERE level_id = lid AND sort_order = tp;
                IF tid IS NULL THEN
                    INSERT INTO topics (level_id, title, sort_order, is_free_trial, status)
                    VALUES (lid, 'Chủ đề ' || lv || '.' || tp, tp, tp = 1, 'PUBLISHED')
                    RETURNING id INTO tid;
                END IF;

                IF NOT EXISTS (SELECT 1 FROM lessons WHERE topic_id = tid AND sort_order = 1) THEN
                    INSERT INTO lessons (topic_id, type, title, duration_sec, sort_order, status)
                    VALUES (tid, 'CONNECTIVE_VIDEO', 'Từ nối ' || lv || '.' || tp, 300, 1, 'PUBLISHED');
                END IF;
                IF NOT EXISTS (SELECT 1 FROM lessons WHERE topic_id = tid AND sort_order = 2) THEN
                    INSERT INTO lessons (topic_id, type, title, duration_sec, sort_order, status)
                    VALUES (tid, 'REFLEX_VIDEO', 'Phản xạ ' || lv || '.' || tp, 180, 2, 'PUBLISHED');
                END IF;
                tid := NULL;
            END LOOP;
        END LOOP;
    END LOOP;
END $$;

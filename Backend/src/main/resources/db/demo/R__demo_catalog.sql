-- DỮ LIỆU DEMO, KHÔNG CHẠY Ở PRODUCTION. Chỉ nạp khi bật location classpath:db/demo (DEMO_FLYWAY_LOCATION).
-- Idempotent: chạy lại không tạo trùng; khóa theo slug, (course, code) và sort_order.
-- D-54: 6 khóa IELTS theo D-02. Mô tả khóa dưới đây là mô tả DEMO, không phải nội dung chính thức.
-- D-57: chỉ Topic đầu của Level đầu mỗi khóa là học thử.
DO $$
DECLARE
    c RECORD;
    old RECORD;
    cid bigint;
    lid bigint;
    tid bigint;
    lv int;
    tp int;
BEGIN
    -- Dọn 7 khóa demo cũ theo thứ tự FK. Khóa đã có dữ liệu học viên (đăng ký, bài làm...) thì khối con bị hoàn tác
    -- và khóa chỉ chuyển DRAFT để ẩn, không mất dữ liệu người dùng.
    FOR old IN SELECT id FROM courses WHERE slug IN
        ('ielts-0-3', 'ielts-3-5', 'ielts-5-7', 'ielts-7-8', 'cambridge-demo', 'pho-thong-demo', 'giao-tiep-demo') LOOP
        BEGIN
            UPDATE courses SET exam_bank_id = NULL WHERE id = old.id;
            DELETE FROM question_choices WHERE question_id IN (SELECT q.id FROM questions q JOIN question_banks b ON b.id = q.bank_id
                JOIN topics t ON t.id = b.topic_id JOIN course_levels l ON l.id = t.level_id WHERE l.course_id = old.id);
            DELETE FROM question_grammar_tags WHERE question_id IN (SELECT q.id FROM questions q JOIN question_banks b ON b.id = q.bank_id
                JOIN topics t ON t.id = b.topic_id JOIN course_levels l ON l.id = t.level_id WHERE l.course_id = old.id);
            DELETE FROM questions WHERE bank_id IN (SELECT b.id FROM question_banks b
                JOIN topics t ON t.id = b.topic_id JOIN course_levels l ON l.id = t.level_id WHERE l.course_id = old.id);
            DELETE FROM question_banks WHERE topic_id IN (SELECT t.id FROM topics t JOIN course_levels l ON l.id = t.level_id WHERE l.course_id = old.id);
            DELETE FROM lessons WHERE topic_id IN (SELECT t.id FROM topics t JOIN course_levels l ON l.id = t.level_id WHERE l.course_id = old.id);
            DELETE FROM topics WHERE level_id IN (SELECT id FROM course_levels WHERE course_id = old.id);
            -- sản phẩm của khóa bị xóa: ẩn đi (đơn cũ vẫn tham chiếu), rồi gỡ liên kết level
            UPDATE products SET status = 'INACTIVE', updated_at = CURRENT_TIMESTAMP
            WHERE id IN (SELECT product_id FROM product_levels WHERE level_id IN (SELECT id FROM course_levels WHERE course_id = old.id));
            DELETE FROM product_levels WHERE level_id IN (SELECT id FROM course_levels WHERE course_id = old.id);
            DELETE FROM course_levels WHERE course_id = old.id;
            DELETE FROM courses WHERE id = old.id;
        EXCEPTION WHEN foreign_key_violation THEN
            UPDATE courses SET status = 'DRAFT', updated_at = CURRENT_TIMESTAMP WHERE id = old.id;
        END;
    END LOOP;

    FOR c IN
        SELECT * FROM (VALUES
            ('begin',            'Begin',            'Xây nền tảng tiếng Anh từ con số 0, hướng tới band 3.0.',                'IELTS', 0.0, 3.0),
            ('academic-english-1', 'Academic English 1', 'Củng cố ngữ pháp và từ nối nền tảng, đi từ band 3.0 lên 4.0.',     'IELTS', 3.0, 4.0),
            ('academic-english-2', 'Academic English 2', 'Mở rộng cấu trúc câu và cách diễn đạt học thuật, từ band 4.0 lên 5.0.', 'IELTS', 4.0, 5.0),
            ('pre-ielts',        'Pre IELTS',        'Làm quen dạng bài IELTS và nâng độ chính xác, từ band 5.0 lên 5.5.',     'IELTS', 5.0, 5.5),
            ('ielts-foundation', 'IELTS Foundation', 'Vững nền tảng bốn kỹ năng IELTS, từ band 5.5 lên 6.0.',                  'IELTS', 5.5, 6.0),
            ('super-ielts',      'Super IELTS',      'Tăng tốc chiến lược làm bài và diễn đạt tự nhiên, từ band 6.0 lên 7.0.',  'IELTS', 6.0, 7.0)
        ) AS v(slug, title, description, target_type, band_from, band_to)
    LOOP
        INSERT INTO courses (slug, title, description, target_type, status, band_from, band_to)
        VALUES (c.slug, c.title, c.description, c.target_type, 'PUBLISHED', c.band_from, c.band_to)
        ON CONFLICT (slug) DO UPDATE
            SET title = EXCLUDED.title, description = EXCLUDED.description, target_type = EXCLUDED.target_type,
                status = 'PUBLISHED', band_from = EXCLUDED.band_from, band_to = EXCLUDED.band_to, updated_at = CURRENT_TIMESTAMP
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
                    VALUES (lid, 'Chủ đề ' || lv || '.' || tp, tp, lv = 1 AND tp = 1, 'PUBLISHED')
                    RETURNING id INTO tid;
                END IF;

                -- D-58: kỹ năng xoay vòng theo vị trí topic; chạy lại cho cùng kết quả
                UPDATE topics SET skill = (ARRAY['LISTENING', 'READING', 'WRITING', 'SPEAKING', 'GRAMMAR_VOCAB'])[1 + (lv * 2 + tp) % 5]
                WHERE id = tid;

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

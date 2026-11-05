-- DỮ LIỆU DEMO, KHÔNG CHẠY Ở PRODUCTION. Chỉ nạp khi bật location classpath:db/demo (DEMO_FLYWAY_LOCATION).
-- Phụ thuộc R__demo_catalog (chạy trước theo thứ tự tên). Idempotent: sản phẩm khóa theo tên, chỉ tạo khi chưa có.
-- Giá và thời hạn chỉ để thử luồng mua, chưa phải số liệu chính thức (chờ Q-08, Q-09).
DO $$
DECLARE
    lv RECORD;
    course_row RECORD;
    pid bigint;
    pname text;
BEGIN
    -- Mỗi Level một sản phẩm LEVEL
    FOR lv IN
        SELECT l.id AS level_id, c.title AS course_title, l.title AS level_title
        FROM course_levels l JOIN courses c ON c.id = l.course_id
        WHERE c.status = 'PUBLISHED'
    LOOP
        pname := lv.course_title || ' - ' || lv.level_title;
        IF NOT EXISTS (SELECT 1 FROM products WHERE type = 'LEVEL' AND name = pname) THEN
            INSERT INTO products (type, name, price_vnd, access_days, ai_quota)
            VALUES ('LEVEL', pname, 490000, 180, 20)
            RETURNING id INTO pid;
            INSERT INTO product_levels (product_id, level_id) VALUES (pid, lv.level_id);
        END IF;
    END LOOP;

    -- Mỗi Course một COMBO gồm mọi Level
    FOR course_row IN SELECT id, title FROM courses WHERE status = 'PUBLISHED' LOOP
        pname := 'Combo ' || course_row.title;
        IF NOT EXISTS (SELECT 1 FROM products WHERE type = 'COMBO' AND name = pname) THEN
            INSERT INTO products (type, name, price_vnd, access_days, ai_quota)
            VALUES ('COMBO', pname, 890000, 365, 50)
            RETURNING id INTO pid;
            INSERT INTO product_levels (product_id, level_id)
            SELECT pid, id FROM course_levels WHERE course_id = course_row.id;
        END IF;
    END LOOP;

    -- Gói nạp thêm lượt AI: không mở nội dung nên không có product_levels
    IF NOT EXISTS (SELECT 1 FROM products WHERE type = 'AI_ADDON' AND name = 'Nạp thêm 20 lượt AI') THEN
        INSERT INTO products (type, name, price_vnd, access_days, ai_quota)
        VALUES ('AI_ADDON', 'Nạp thêm 20 lượt AI', 99000, NULL, 20);
    END IF;
END $$;

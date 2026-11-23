-- DỮ LIỆU DEMO, KHÔNG CHẠY Ở PRODUCTION. Chỉ nạp khi bật location classpath:db/demo (DEMO_FLYWAY_LOCATION).
-- Phụ thuộc R__demo_catalog (chạy trước theo thứ tự tên). Idempotent: sản phẩm khóa theo tên, chỉ tạo khi chưa có.
-- Giá mẫu, chờ BQ-01/BQ-03 (D-34): chỉ để dựng giao diện, không phải số liệu chính thức.
-- D-25: ghi danh theo khóa. Sản phẩm theo khóa gắn mọi Level của khóa vào product_levels (EntitlementGranter suy ra khóa từ Level).
DO $$
DECLARE
    course_row RECORD;
    tier RECORD;
    addon RECORD;
    pid bigint;
    pname text;
BEGIN
    -- Dọn seed cũ (sản phẩm theo Level, sản phẩm không còn level nào của khóa đang xuất bản): ẩn đi, không xóa vì có thể đã có đơn tham chiếu.
    UPDATE products SET status = 'INACTIVE', updated_at = CURRENT_TIMESTAMP
    WHERE status = 'ACTIVE' AND type = 'LEVEL' AND (name NOT LIKE '% - % ngày' OR NOT EXISTS (
        SELECT 1 FROM product_levels pl JOIN course_levels lv ON lv.id = pl.level_id JOIN courses c ON c.id = lv.course_id
        WHERE pl.product_id = products.id AND c.status = 'PUBLISHED'));

    -- D-54/D-02: giá 6 tháng theo bảng; D-34: 12 tháng = 1,6 x, làm tròn đến nghìn. type 'LEVEL' là giá trị khóa học còn hợp lệ.
    FOR course_row IN
        SELECT c.id, c.title, v.price FROM courses c
        JOIN (VALUES ('begin', 5600000::bigint), ('academic-english-1', 4200000::bigint), ('academic-english-2', 4200000::bigint),
                     ('pre-ielts', 7350000::bigint), ('ielts-foundation', 11025000::bigint), ('super-ielts', 7875000::bigint)
        ) AS v(slug, price) ON v.slug = c.slug
        WHERE c.status = 'PUBLISHED' LOOP
        FOR tier IN SELECT 180 AS days, course_row.price AS price
                    UNION ALL SELECT 365, (round(course_row.price * 1.6 / 1000) * 1000)::bigint LOOP
            pname := course_row.title || ' - ' || tier.days || ' ngày';
            IF EXISTS (SELECT 1 FROM products WHERE type = 'LEVEL' AND name = pname) THEN
                UPDATE products SET price_vnd = tier.price, access_days = tier.days, status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP
                WHERE type = 'LEVEL' AND name = pname;
            ELSE
                INSERT INTO products (type, name, price_vnd, access_days, ai_quota)
                VALUES ('LEVEL', pname, tier.price, tier.days, 0) -- lượt AI của khóa theo cấu hình khóa, không cộng pool (D-47)
                RETURNING id INTO pid;
                INSERT INTO product_levels (product_id, level_id)
                SELECT pid, id FROM course_levels WHERE course_id = course_row.id;
            END IF;
        END LOOP;
    END LOOP;

    -- Sáu gói nạp lượt AI: không mở nội dung nên không có product_levels. Chạy lại thì cập nhật giá mẫu.
    FOR addon IN SELECT * FROM (VALUES (10, 49000::bigint), (20, 89000::bigint), (50, 199000::bigint),
                                       (100, 369000::bigint), (200, 699000::bigint), (500, 1590000::bigint)) AS a(quota, price) LOOP
        pname := 'Nạp thêm ' || addon.quota || ' lượt AI';
        IF EXISTS (SELECT 1 FROM products WHERE type = 'AI_ADDON' AND name = pname) THEN
            UPDATE products SET price_vnd = addon.price, ai_quota = addon.quota, status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP
            WHERE type = 'AI_ADDON' AND name = pname;
        ELSE
            INSERT INTO products (type, name, price_vnd, access_days, ai_quota)
            VALUES ('AI_ADDON', pname, addon.price, NULL, addon.quota);
        END IF;
    END LOOP;
END $$;

-- DỮ LIỆU DEMO, KHÔNG CHẠY Ở PRODUCTION. Chỉ nạp khi bật location classpath:db/demo (DEMO_FLYWAY_LOCATION).
-- Phụ thuộc R__demo_catalog (chạy trước theo thứ tự tên). Idempotent: sản phẩm khóa theo tên, chỉ tạo khi chưa có.
-- Giá mẫu, chờ BQ-01/BQ-03 (D-34): chỉ để dựng giao diện, không phải số liệu chính thức.
-- D-25: ghi danh theo khóa. Sản phẩm theo khóa gắn mọi Level của khóa vào product_levels (EntitlementGranter suy ra khóa từ Level).
-- D-03: không seed COMBO. Giá trị COMBO vẫn nằm trong CHECK của bảng products để migration không phá vỡ dữ liệu cũ.
DO $$
DECLARE
    course_row RECORD;
    tier RECORD;
    addon RECORD;
    pid bigint;
    pname text;
BEGIN
    -- Dọn seed cũ (sản phẩm theo Level và COMBO): ẩn đi, không xóa vì có thể đã có đơn tham chiếu.
    UPDATE products SET status = 'INACTIVE', updated_at = CURRENT_TIMESTAMP
    WHERE status = 'ACTIVE' AND (type = 'COMBO' OR (type = 'LEVEL' AND name NOT LIKE '% - % ngày'));

    -- Mỗi khóa hai sản phẩm: 180 ngày và 365 ngày (giá 365 = 1,6 x giá 180). type 'LEVEL' là giá trị khóa học còn hợp lệ.
    FOR course_row IN SELECT id, title FROM courses WHERE status = 'PUBLISHED' LOOP
        FOR tier IN SELECT * FROM (VALUES (180, 4200000::bigint), (365, 6720000::bigint)) AS t(days, price) LOOP
            pname := course_row.title || ' - ' || tier.days || ' ngày';
            IF NOT EXISTS (SELECT 1 FROM products WHERE type = 'LEVEL' AND name = pname) THEN
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

-- Chỉ PostgreSQL: bỏ CHECK inline cũ của V5 (còn COMBO); ck_products_type ở V11 thay thế. H2 tự đặt tên khác nên giữ nguyên, vô hại.
-- IF EXISTS: tên tự sinh chưa kiểm trên DB thật; nếu khác thì CHECK cũ còn lại cũng vô hại.
ALTER TABLE products DROP CONSTRAINT IF EXISTS products_type_check;

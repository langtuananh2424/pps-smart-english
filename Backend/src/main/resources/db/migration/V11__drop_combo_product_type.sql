-- D-03 (FR-COM-01): không bán combo. Dòng COMBO cũ đã ẩn chỉ đổi type, không xóa vì có thể có đơn tham chiếu.
UPDATE products SET type = 'LEVEL', status = 'INACTIVE' WHERE type = 'COMBO';
ALTER TABLE products ADD CONSTRAINT ck_products_type CHECK (type IN ('LEVEL', 'AI_ADDON'));

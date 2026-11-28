-- D-176: mốc "lần xuất trước" tính theo bộ lọc của lần xuất. null = tất cả (bản ghi cũ là xuất toàn bộ).
ALTER TABLE teaching_report_exports ADD COLUMN role varchar(10);
ALTER TABLE teaching_report_exports ADD COLUMN class_id bigint REFERENCES classes (id);

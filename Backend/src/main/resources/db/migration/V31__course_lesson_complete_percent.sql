-- FR-LMS-01, FR-LMS-03, D-124, D-138: % thời lượng xem để tính xong bài giảng, đặt theo khóa. Mặc định 80 giữ hành vi cũ.
-- Chỉ thêm cột có mặc định (tương thích ngược). Bài đã hoàn thành (completed_at) giữ nguyên khi đổi ngưỡng.
ALTER TABLE courses ADD COLUMN lesson_complete_percent integer NOT NULL DEFAULT 80
    CONSTRAINT chk_courses_lesson_complete_percent CHECK (lesson_complete_percent BETWEEN 1 AND 100);

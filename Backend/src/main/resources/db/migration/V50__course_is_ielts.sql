-- D-161: khóa IELTS do Phòng Đào tạo đánh dấu; CMS chỉ cho soạn kiểu câu IELTS_* trong bộ của khóa này. Mặc định false nên tương thích ngược.
ALTER TABLE courses ADD COLUMN is_ielts boolean NOT NULL DEFAULT false;

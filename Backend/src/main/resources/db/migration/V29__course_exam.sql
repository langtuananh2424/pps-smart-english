-- FR-EXAM-02, D-46: mỗi khóa có một bài thi (bộ câu hỏi mode EXAM), ngưỡng % học xong để mở và số lần thi lại.
ALTER TABLE courses ADD COLUMN exam_bank_id bigint REFERENCES question_banks (id);
ALTER TABLE courses ADD COLUMN exam_unlock_percent integer NOT NULL DEFAULT 80;
ALTER TABLE courses ADD COLUMN exam_retakes integer NOT NULL DEFAULT 0;
ALTER TABLE courses ADD CONSTRAINT ck_courses_exam_unlock_percent CHECK (exam_unlock_percent BETWEEN 0 AND 100);
ALTER TABLE courses ADD CONSTRAINT ck_courses_exam_retakes CHECK (exam_retakes >= 0);
-- Nhiều NULL vẫn hợp lệ; một bộ đề chỉ làm bài thi của một khóa.
ALTER TABLE courses ADD CONSTRAINT uq_courses_exam_bank UNIQUE (exam_bank_id);

-- FR-LMS-04, FR-EXAM-02, D-118, D-122, D-124: học tuần tự theo khóa và bài kiểm tra theo chương (Topic = chương/unit).
-- Chỉ thêm cột có giá trị mặc định nên bản chạy cũ vẫn tương thích. Bài thi cuối khóa giữ nguyên courses.exam_bank_id.
ALTER TABLE courses ADD COLUMN sequential boolean NOT NULL DEFAULT FALSE;
-- Mỗi topic tối đa một bài kiểm tra chương: bộ câu hỏi mode EXAM của chính topic; số lần làm lại cài theo chương (mặc định 0 = một lần).
ALTER TABLE topics ADD COLUMN chapter_test_bank_id bigint REFERENCES question_banks (id);
ALTER TABLE topics ADD COLUMN chapter_test_retakes integer NOT NULL DEFAULT 0 CHECK (chapter_test_retakes >= 0);

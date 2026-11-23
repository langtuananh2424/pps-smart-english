-- FR-ASG-02: mỗi bài giao nộp một lần, chặn cả khi hai lượt nói xác nhận đồng thời. Chỉ PostgreSQL (H2 không có partial index).
-- UNCLEAR, FAILED nằm ngoài điều kiện nên học viên thu lại được, đúng quy tắc SpeakingSubmission.SUBMITTED_OK.
-- Phiên viết không có chỉ mục tương tự: phiên có job chấm lỗi hẳn được nộp lại, điều kiện đó nằm ở ai_jobs nên partial index không biểu diễn được.
CREATE UNIQUE INDEX uq_speaking_assignment_submitted ON speaking_submissions (assignment_id, user_id)
    WHERE assignment_id IS NOT NULL AND status IN ('GRADING', 'GRADED');

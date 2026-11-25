-- D-147: câu Viết/Nói chấm lỗi sau khi lượt đã nộp được chấm lại tự động đúng một lần; cờ này chặn lần thứ hai.
ALTER TABLE ai_jobs ADD COLUMN auto_retried boolean NOT NULL DEFAULT false;
-- Lý do câu Viết/Nói không chấm được (tính 0 điểm, đã hoàn lượt AI); NULL với mọi câu còn lại. Cột nullable nên bản cũ vẫn chạy.
ALTER TABLE student_answers ADD COLUMN ai_fail_reason varchar(200);
-- Lượt AI của câu đó đã được hoàn (bài giao không trừ lượt nên không có gì để hoàn).
ALTER TABLE student_answers ADD COLUMN ai_fail_refunded boolean NOT NULL DEFAULT false;

-- FR-EXAM-03, D-12: bảng quy đổi số câu đúng -> band theo từng đề thi khóa. Bảng mới, không đụng dữ liệu cũ.
-- band không chặn trên 9: thang điểm theo chương trình (D-105, D-132), không cố định IELTS.
CREATE TABLE exam_band_rules (
    id          bigserial PRIMARY KEY,
    bank_id     bigint        NOT NULL REFERENCES question_banks (id),
    skill       varchar(20)   NOT NULL CHECK (skill IN ('LISTENING', 'READING', 'GRAMMAR')),
    min_correct integer       NOT NULL CHECK (min_correct >= 0),
    band        numeric(5, 1) NOT NULL CHECK (band >= 0),
    CONSTRAINT uq_exam_band_rules UNIQUE (bank_id, skill, min_correct)
);

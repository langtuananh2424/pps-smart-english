-- FR-AI-06, FR-AI-07, D-28: nội dung soạn sẵn cho Cam (gợi ý theo câu, hỏi đáp theo bộ câu hỏi); lúc chạy không gọi AI.
CREATE TABLE question_hints (
    id          bigserial PRIMARY KEY,
    question_id bigint       NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    sort_order  integer      NOT NULL DEFAULT 0,
    content     varchar(500) NOT NULL,
    detail      text
);
CREATE INDEX idx_question_hints_question ON question_hints (question_id, sort_order);

CREATE TABLE bank_faqs (
    id         bigserial PRIMARY KEY,
    bank_id    bigint       NOT NULL REFERENCES question_banks (id) ON DELETE CASCADE,
    sort_order integer      NOT NULL DEFAULT 0,
    question   varchar(300) NOT NULL,
    answer     text         NOT NULL
);
CREATE INDEX idx_bank_faqs_bank ON bank_faqs (bank_id, sort_order);

-- #66 (D-133): nhóm câu hỏi (bài đọc / phần nghe dùng chung) và nội dung giàu định dạng.
-- *_html đã được server làm sạch; cột chữ thường vẫn giữ và do server suy ra từ HTML. NULL = chỉ có chữ thường (dữ liệu cũ).
CREATE TABLE question_groups (
    id          bigserial PRIMARY KEY,
    bank_id     bigint       NOT NULL REFERENCES question_banks (id) ON DELETE CASCADE,
    sort_order  integer      NOT NULL DEFAULT 0,
    title       varchar(200),
    instruction varchar(300),
    passage_html text,
    created_at  timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_question_groups_bank ON question_groups (bank_id, sort_order);

-- Không cascade: xóa nhóm còn câu hỏi phải bị chặn.
ALTER TABLE questions ADD COLUMN group_id bigint REFERENCES question_groups (id);
CREATE INDEX idx_questions_group ON questions (group_id);
ALTER TABLE questions ADD COLUMN prompt_html text;
ALTER TABLE question_choices ADD COLUMN content_html text;

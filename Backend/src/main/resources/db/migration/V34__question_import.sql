-- #70 (FR-ADM-02, D-133, D-138): nhập hàng loạt theo lô, hoàn tác lô, gợi ý câu trùng.
-- Chỉ thêm bảng và cột null được nên deploy rolling an toàn. prompt_hash của câu cũ được điền lười khi cần so trùng.
CREATE TABLE import_batches (
    id             bigserial PRIMARY KEY,
    bank_id        bigint      NOT NULL REFERENCES question_banks (id),
    user_id        bigint      NOT NULL REFERENCES users (id),
    source         varchar(10) NOT NULL CHECK (source IN ('PASTE', 'EXCEL')),
    status         varchar(10) NOT NULL DEFAULT 'APPLIED' CHECK (status IN ('APPLIED', 'UNDONE')),
    question_count integer     NOT NULL,
    created_at     timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    undone_at      timestamp with time zone
);
CREATE INDEX idx_import_batches_bank ON import_batches (bank_id, id DESC);

-- archived_at: câu bị ẩn khi hoàn tác lô mà đã có lượt làm; không hiện cho lượt làm mới, vẫn giữ lịch sử học viên.
ALTER TABLE questions ADD COLUMN import_batch_id bigint REFERENCES import_batches (id);
ALTER TABLE questions ADD COLUMN archived_at timestamp with time zone;
ALTER TABLE questions ADD COLUMN prompt_hash varchar(64);
CREATE INDEX idx_questions_bank_hash ON questions (bank_id, prompt_hash);
CREATE INDEX idx_questions_import_batch ON questions (import_batch_id);

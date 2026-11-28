-- FR-LMS-02, FR-ADM-01 (D-171, D-175): phụ đề, glossary của bài giảng và từ điển gợi ý nghĩa.
-- Phụ đề lưu trong CSDL (không phải MinIO như system-design 3.x storage_key): file nhỏ, web tự vẽ phụ đề nên cần cue dạng JSON, tránh CORS.
-- content là VTT đã chuẩn hóa; cue_count để CMS hiện số cue mà không đọc content.
CREATE TABLE lesson_subtitles (
    id         bigserial PRIMARY KEY,
    lesson_id  bigint      NOT NULL REFERENCES lessons (id) ON DELETE CASCADE,
    lang       varchar(2)  NOT NULL CHECK (lang IN ('en', 'vi')),
    content    text        NOT NULL,
    cue_count  integer     NOT NULL,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_lesson_subtitles UNIQUE (lesson_id, lang)
);

-- phrase_key = phrase viết thường, để chặn trùng không phân biệt hoa thường trong cùng bài.
CREATE TABLE lesson_glossary (
    id             bigserial PRIMARY KEY,
    lesson_id      bigint       NOT NULL REFERENCES lessons (id) ON DELETE CASCADE,
    phrase         varchar(100) NOT NULL,
    phrase_key     varchar(100) NOT NULL,
    part_of_speech varchar(30),
    meaning_vi     varchar(500) NOT NULL,
    example        varchar(500),
    source         varchar(12)  NOT NULL CHECK (source IN ('MANUAL', 'WIKTIONARY')),
    created_at     timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_lesson_glossary UNIQUE (lesson_id, phrase_key)
);

-- Chỉ có dữ liệu nhập từ Wiktionary (DictionaryImporter, D-175); mỗi lần nhập xóa hết rồi nạp lại.
CREATE TABLE dictionary_entries (
    id             bigserial PRIMARY KEY,
    word           varchar(100) NOT NULL,
    word_key       varchar(100) NOT NULL,
    part_of_speech varchar(30),
    meaning_vi     varchar(500) NOT NULL,
    example        varchar(500)
);
CREATE INDEX idx_dictionary_word_key ON dictionary_entries (word_key);

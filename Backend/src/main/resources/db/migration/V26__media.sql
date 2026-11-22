-- #65 (D-134): ảnh của câu hỏi. status PENDING: đã cấp quyền tải lên, file nằm ở upload_key; READY: đã xử lý, file cuối ở storage_key.
-- Chỉ giữ bản đã xử lý (WebP, đã thu nhỏ, không EXIF). kind AUDIO dành cho bước sau (#69).
CREATE TABLE media_assets (
    id                bigserial PRIMARY KEY,
    kind              varchar(10)  NOT NULL CHECK (kind IN ('IMAGE', 'AUDIO')),
    status            varchar(10)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'READY')),
    upload_key        varchar(300),
    storage_key       varchar(300) UNIQUE,
    upload_expires_at timestamp with time zone,
    max_bytes         bigint,
    mime              varchar(60),
    size_bytes        bigint,
    width             integer,
    height            integer,
    duration_ms       integer,
    uploaded_by       bigint       NOT NULL REFERENCES users (id),
    created_at        timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_media_assets_pending ON media_assets (status, created_at);

-- display_size: phần trăm khung hiển thị, mỗi ảnh mỗi câu một cỡ. role INLINE do #66 dùng (ảnh trong HTML).
CREATE TABLE question_media (
    id           bigserial PRIMARY KEY,
    question_id  bigint       NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    asset_id     bigint       NOT NULL REFERENCES media_assets (id),
    role         varchar(10)  NOT NULL CHECK (role IN ('INLINE', 'DIAGRAM')),
    display_size varchar(10)  NOT NULL CHECK (display_size IN ('SM', 'MD', 'LG', 'FULL')),
    alt_text     varchar(300) NOT NULL,
    sort_order   integer      NOT NULL DEFAULT 0,
    CONSTRAINT uq_question_media UNIQUE (question_id, asset_id)
);
CREATE INDEX idx_question_media_question ON question_media (question_id, sort_order);

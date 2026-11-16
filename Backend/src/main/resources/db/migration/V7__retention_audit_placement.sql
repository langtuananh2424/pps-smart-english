-- Giữ chân học viên (streak, XP, thông báo), nhật ký thao tác nội bộ và kiểm tra đầu vào.
-- Cột JSON dùng kiểu json (chạy được cả H2 lẫn PostgreSQL); db/migration-pg/V100 đổi sang jsonb trên PostgreSQL.
CREATE TABLE activity_days (
    user_id       bigint NOT NULL REFERENCES users (id),
    activity_date date   NOT NULL, -- ngày theo Asia/Ho_Chi_Minh (Q-18); không đặt tên "day" vì DAY là từ khóa của H2
    PRIMARY KEY (user_id, activity_date)
);

CREATE TABLE xp_events (
    id         bigserial PRIMARY KEY,
    user_id    bigint      NOT NULL REFERENCES users (id),
    source     varchar(30) NOT NULL CHECK (source IN ('LESSON', 'EXERCISE', 'EXERCISE_HIGH')),
    ref_id     bigint      NOT NULL,
    xp         integer     NOT NULL CHECK (xp > 0),
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_xp_events_user_source_ref UNIQUE (user_id, source, ref_id) -- không cộng XP hai lần cho cùng một việc
);

CREATE TABLE push_subscriptions (
    id         bigserial PRIMARY KEY,
    user_id    bigint        NOT NULL REFERENCES users (id),
    channel    varchar(10)   NOT NULL DEFAULT 'WEBPUSH' CHECK (channel IN ('WEBPUSH', 'FCM')),
    endpoint   varchar(1000) NOT NULL,
    keys       json,         -- {p256dh, auth}
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_push_subscriptions_endpoint UNIQUE (endpoint)
);
CREATE INDEX idx_push_subscriptions_user ON push_subscriptions (user_id);

CREATE TABLE notifications (
    id         bigserial PRIMARY KEY,
    user_id    bigint       NOT NULL REFERENCES users (id),
    type       varchar(30)  NOT NULL CHECK (type IN ('STREAK_REMINDER', 'WRITING_GRADED')),
    payload    json         NOT NULL,  -- {title, body, url}
    dedupe_key varchar(100) NOT NULL,  -- chống tạo/gửi hai lần cùng một thông báo
    sent_at    timestamp with time zone,
    read_at    timestamp with time zone,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_notifications_user_dedupe UNIQUE (user_id, dedupe_key)
);
CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);

-- FR-ADM-05: chỉ ghi thêm; bất biến trên PostgreSQL ở db/migration-pg/V100
CREATE TABLE audit_logs (
    id           bigserial PRIMARY KEY,
    actor_id     bigint       NOT NULL REFERENCES users (id),
    action       varchar(100) NOT NULL,
    target_type  varchar(50)  NOT NULL,
    target_id    varchar(100) NOT NULL,
    before_value json,
    after_value  json,
    reason       varchar(500),
    created_at   timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_audit_logs_actor ON audit_logs (actor_id, id);
CREATE INDEX idx_audit_logs_target ON audit_logs (target_type, target_id, id);
CREATE INDEX idx_audit_logs_created ON audit_logs (created_at);

-- FR-TEST-01/02, FR-GOAL-03, D-23, D-39: đề đầu vào dùng lại question_banks/questions (purpose PLACEMENT).
-- Một dòng duy nhất. max_retakes null = không cho làm lại (D-23).
CREATE TABLE placement_config (
    id          integer PRIMARY KEY CHECK (id = 1),
    max_retakes integer CHECK (max_retakes IS NULL OR max_retakes >= 0)
);
INSERT INTO placement_config (id, max_retakes) VALUES (1, NULL);

-- % đúng của kỹ năng -> band: lấy dòng có min_percent lớn nhất không vượt % đúng.
CREATE TABLE placement_band_rules (
    id          bigserial PRIMARY KEY,
    skill       varchar(20)   NOT NULL CHECK (skill IN ('LISTENING', 'READING', 'GRAMMAR')),
    min_percent integer       NOT NULL CHECK (min_percent BETWEEN 0 AND 100),
    band        numeric(2, 1) NOT NULL CHECK (band BETWEEN 0 AND 9),
    CONSTRAINT uq_placement_band_rules UNIQUE (skill, min_percent)
);

-- Lượt làm của khách hoặc học viên. Khách xác thực bằng guest_token_hash (SHA-256 của token), claim sẽ gắn user_id.
CREATE TABLE placement_attempts (
    id               bigserial PRIMARY KEY,
    bank_id          bigint      NOT NULL REFERENCES question_banks (id),
    user_id          bigint REFERENCES users (id),
    guest_token_hash varchar(64),
    started_at       timestamp with time zone NOT NULL,
    time_limit_sec   integer     NOT NULL,
    deadline_at      timestamp with time zone NOT NULL,
    draft_answers    json,
    answers          json,
    submitted_at     timestamp with time zone,
    skill_results    json,
    overall_band     numeric(2, 1),
    claimed_at       timestamp with time zone,
    CONSTRAINT ck_placement_attempts_owner CHECK (user_id IS NOT NULL OR guest_token_hash IS NOT NULL)
);
CREATE INDEX idx_placement_attempts_user ON placement_attempts (user_id, id DESC);
CREATE INDEX idx_placement_attempts_deadline ON placement_attempts (deadline_at);

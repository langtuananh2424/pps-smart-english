-- Cột JSON dùng kiểu json (chạy được cả H2 lẫn PostgreSQL); db/migration-pg/V15 đổi sang jsonb trên PostgreSQL.
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

CREATE TABLE user_profiles (
    user_id          bigint PRIMARY KEY REFERENCES users (id),
    display_name     varchar(100) NOT NULL,
    self_level       varchar(20),
    xp               bigint       NOT NULL DEFAULT 0,
    streak_current   integer      NOT NULL DEFAULT 0,
    streak_best      integer      NOT NULL DEFAULT 0,
    last_active_date date,
    timezone         varchar(64), -- để null: múi giờ chưa chốt (Q-18)
    created_at       timestamp with time zone  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       timestamp with time zone  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_goals (
    id           bigserial PRIMARY KEY,
    user_id      bigint       NOT NULL REFERENCES users (id),
    course_id    bigint, -- chưa có FK: bảng course chưa tồn tại
    target_type  varchar(100) NOT NULL,
    target_value varchar(100) NOT NULL,
    target_date  date,
    created_at   timestamp with time zone  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   timestamp with time zone  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_user_goals_user ON user_goals (user_id);

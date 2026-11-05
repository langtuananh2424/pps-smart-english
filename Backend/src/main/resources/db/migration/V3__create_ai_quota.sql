CREATE TABLE user_ai_quota (
    user_id    bigint PRIMARY KEY REFERENCES users (id),
    balance    integer     NOT NULL DEFAULT 0 CHECK (balance >= 0),
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

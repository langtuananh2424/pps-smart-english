CREATE TABLE users (
    id                bigserial PRIMARY KEY,
    email             varchar(255) NOT NULL,
    email_verified_at timestamp with time zone,
    password_hash     varchar(100),
    role              varchar(20)  NOT NULL DEFAULT 'STUDENT',
    status            varchar(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at        timestamp with time zone  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        timestamp with time zone  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE user_identities (
    id               bigserial PRIMARY KEY,
    user_id          bigint       NOT NULL REFERENCES users (id),
    provider         varchar(20)  NOT NULL,
    provider_subject varchar(255) NOT NULL,
    created_at       timestamp with time zone  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       timestamp with time zone  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_identities_subject UNIQUE (provider, provider_subject)
);
CREATE INDEX idx_user_identities_user ON user_identities (user_id);

CREATE TABLE refresh_tokens (
    id           bigserial PRIMARY KEY,
    user_id      bigint      NOT NULL REFERENCES users (id),
    token_hash   varchar(64) NOT NULL,
    expires_at   timestamp with time zone NOT NULL,
    revoked_at   timestamp with time zone,
    replaced_at  timestamp with time zone,
    device_label varchar(255),
    created_at   timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE TABLE email_tokens (
    id         bigserial PRIMARY KEY,
    user_id    bigint      NOT NULL REFERENCES users (id),
    purpose    varchar(10) NOT NULL,
    token_hash varchar(64) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    used_at    timestamp with time zone,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_email_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX idx_email_tokens_user ON email_tokens (user_id);

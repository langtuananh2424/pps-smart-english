-- raw_payload dùng kiểu json (chạy được cả H2 lẫn PostgreSQL); db/migration-pg/V10 đổi sang jsonb trên PostgreSQL.
CREATE TABLE products (
    id          bigserial PRIMARY KEY,
    type        varchar(20)  NOT NULL CHECK (type IN ('LEVEL', 'COMBO', 'AI_ADDON')),
    name        varchar(200) NOT NULL,
    price_vnd   bigint       NOT NULL CHECK (price_vnd >= 0),
    access_days integer CHECK (access_days > 0), -- null: không giới hạn thời gian
    ai_quota    integer CHECK (ai_quota >= 0),   -- null: không cấp lượt AI
    status      varchar(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at  timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE product_levels (
    product_id bigint NOT NULL REFERENCES products (id),
    level_id   bigint NOT NULL REFERENCES course_levels (id),
    PRIMARY KEY (product_id, level_id)
);

CREATE TABLE orders (
    id         bigserial PRIMARY KEY,
    user_id    bigint      NOT NULL REFERENCES users (id),
    code       varchar(20) NOT NULL,
    status     varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PAID', 'EXPIRED', 'CANCELLED')),
    total_vnd  bigint      NOT NULL CHECK (total_vnd >= 0),
    paid_at    timestamp with time zone,
    expires_at timestamp with time zone NOT NULL, -- hạn thanh toán
    provider   varchar(30),                       -- cổng đã tạo link, để hiện lại QR khi đơn còn PENDING
    qr_content text,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_orders_code UNIQUE (code)
);
CREATE INDEX idx_orders_user_created ON orders (user_id, created_at DESC);
CREATE INDEX idx_orders_status_expires ON orders (status, expires_at);

CREATE TABLE order_items (
    id         bigserial PRIMARY KEY,
    order_id   bigint NOT NULL REFERENCES orders (id),
    product_id bigint NOT NULL REFERENCES products (id),
    price_vnd  bigint NOT NULL CHECK (price_vnd >= 0), -- giá lúc mua
    CONSTRAINT uq_order_items_order_product UNIQUE (order_id, product_id)
);

CREATE TABLE payment_transactions (
    id           bigserial PRIMARY KEY,
    order_id     bigint REFERENCES orders (id), -- null: webhook không khớp đơn nào
    provider     varchar(30)  NOT NULL,
    provider_ref varchar(100) NOT NULL,
    amount_vnd   bigint       NOT NULL,
    status       varchar(30)  NOT NULL CHECK (status IN
                     ('APPLIED', 'MISMATCH', 'ORDER_NOT_FOUND', 'ORDER_CANCELLED', 'ORDER_ALREADY_PAID')),
    raw_payload  json,
    received_at  timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payment_transactions_provider_ref UNIQUE (provider, provider_ref)
);
CREATE INDEX idx_payment_transactions_order ON payment_transactions (order_id);

CREATE TABLE user_course_enrollments (
    id         bigserial PRIMARY KEY,
    user_id    bigint      NOT NULL REFERENCES users (id),
    level_id   bigint      NOT NULL REFERENCES course_levels (id),
    order_id   bigint      REFERENCES orders (id), -- đơn gần nhất cấp hoặc gia hạn
    status     varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'EXPIRED')),
    starts_at  timestamp with time zone NOT NULL,
    expires_at timestamp with time zone, -- null: không giới hạn
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_enrollments_user_level UNIQUE (user_id, level_id)
);

CREATE TABLE ai_quota_ledger (
    id         bigserial PRIMARY KEY,
    user_id    bigint      NOT NULL REFERENCES users (id),
    delta      integer     NOT NULL,
    reason     varchar(20) NOT NULL CHECK (reason IN ('PURCHASE', 'GRADING', 'REFUND', 'ACTIVATION_LINK')),
    ref_id     bigint,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_ai_quota_ledger_user ON ai_quota_ledger (user_id, created_at);

CREATE TABLE idempotency_keys (
    id              bigserial PRIMARY KEY,
    user_id         bigint       NOT NULL REFERENCES users (id),
    idem_key        varchar(100) NOT NULL,
    request_hash    varchar(64)  NOT NULL,
    response_status integer      NOT NULL,
    response_body   text         NOT NULL,
    created_at      timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_idempotency_user_key UNIQUE (user_id, idem_key)
);
CREATE INDEX idx_idempotency_created ON idempotency_keys (created_at);

-- Sản phẩm, đơn hàng, ghi danh theo khóa (D-25), sổ cái lượt AI và link kích hoạt (FR-COM-04).
-- raw_payload dùng kiểu json (chạy được cả H2 lẫn PostgreSQL); db/migration-pg/V100 đổi sang jsonb trên PostgreSQL.
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
-- Báo cáo Admin (FR-ADM-03) lọc theo khoảng thời gian
CREATE INDEX idx_orders_paid_at ON orders (paid_at);

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
    order_id   bigint      REFERENCES orders (id), -- đơn gần nhất cấp hoặc gia hạn; null: cấp từ link kích hoạt
    status     varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'EXPIRED')),
    starts_at  timestamp with time zone NOT NULL,
    expires_at timestamp with time zone, -- null: không giới hạn
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    course_id  bigint      NOT NULL,
    CONSTRAINT fk_enrollments_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT uq_enrollments_user_course UNIQUE (user_id, course_id)
);

-- Số dư lượt AI mua thêm (pool)
CREATE TABLE user_ai_quota (
    user_id    bigint PRIMARY KEY REFERENCES users (id),
    balance    integer     NOT NULL DEFAULT 0 CHECK (balance >= 0),
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Sổ cái ghi nguồn (PERIODIC hoặc POOL). Mỗi (lý do, tham chiếu, nguồn) chỉ một dòng: PURCHASE một lần mỗi đơn,
-- GRADING và REFUND một lần mỗi job và mỗi nguồn. Là lưới an toàn thứ hai chống hoàn lượt hai lần
-- (lưới thứ nhất là chuyển trạng thái job có điều kiện). ACTIVATION_LINK: ref_id = activation_redemptions.id.
CREATE TABLE ai_quota_ledger (
    id         bigserial PRIMARY KEY,
    user_id    bigint      NOT NULL REFERENCES users (id),
    delta      integer     NOT NULL,
    reason     varchar(20) NOT NULL CHECK (reason IN ('PURCHASE', 'GRADING', 'REFUND', 'ACTIVATION_LINK')),
    ref_id     bigint,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source     varchar(10) NOT NULL DEFAULT 'POOL',
    course_id  bigint,
    CONSTRAINT ck_ai_quota_ledger_source CHECK (source IN ('PERIODIC', 'POOL'))
);
CREATE INDEX idx_ai_quota_ledger_user ON ai_quota_ledger (user_id, created_at);
CREATE UNIQUE INDEX uq_ai_quota_ledger_reason_ref_source ON ai_quota_ledger (reason, ref_id, source);

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

CREATE TABLE activation_links (
    id         bigserial PRIMARY KEY,
    token_hash varchar(64) NOT NULL, -- chỉ lưu hash của mã, mã thô chỉ trả một lần lúc tạo
    product_id bigint      NOT NULL REFERENCES products (id),
    max_uses   integer     NOT NULL CHECK (max_uses >= 1),
    used_count integer     NOT NULL DEFAULT 0 CHECK (used_count >= 0),
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    note       varchar(500),
    created_by bigint      NOT NULL REFERENCES users (id),
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_activation_links_token UNIQUE (token_hash)
);

CREATE TABLE activation_redemptions (
    id          bigserial PRIMARY KEY,
    link_id     bigint NOT NULL REFERENCES activation_links (id),
    user_id     bigint NOT NULL REFERENCES users (id),
    redeemed_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_activation_redemptions_link_user UNIQUE (link_id, user_id)
);

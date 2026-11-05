-- FR-COM-04. user_course_enrollments.order_id đã cho phép null từ V9 (link không có đơn);
-- ai_quota_ledger.reason đã gồm ACTIVATION_LINK (ref_id = activation_redemptions.id).
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

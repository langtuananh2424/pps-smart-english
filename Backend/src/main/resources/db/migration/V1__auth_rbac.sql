-- Tài khoản, phiên đăng nhập và phân quyền (FR-AUTH, FR-ADM-04, D-24, D-37).
-- Role là tập quyền; người dùng có nhiều role (kể cả STUDENT), được GRANT/REVOKE từng quyền có hạn tùy chọn.
CREATE TABLE users (
    id                bigserial PRIMARY KEY,
    email             varchar(255) NOT NULL,
    email_verified_at timestamp with time zone,
    password_hash     varchar(100),
    status            varchar(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at        timestamp with time zone  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        timestamp with time zone  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_email UNIQUE (email)
);
-- Báo cáo Admin (FR-ADM-03) lọc theo khoảng thời gian
CREATE INDEX idx_users_created_at ON users (created_at);

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

CREATE TABLE roles (
    id         bigserial PRIMARY KEY,
    code       varchar(50)  NOT NULL UNIQUE,
    name       varchar(100) NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE permissions (
    id          bigserial PRIMARY KEY,
    code        varchar(100) NOT NULL UNIQUE,  -- module.action
    module      varchar(50)  NOT NULL,
    description varchar(255) NOT NULL
);

CREATE TABLE role_permissions (
    role_id       bigint NOT NULL REFERENCES roles (id),
    permission_id bigint NOT NULL REFERENCES permissions (id),
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE user_roles (
    user_id    bigint NOT NULL REFERENCES users (id),
    role_id    bigint NOT NULL REFERENCES roles (id),
    granted_by bigint REFERENCES users (id),
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id)
);

-- Một người chỉ có một dòng override cho một quyền; sửa thì ghi đè. expires_at null = không hết hạn.
CREATE TABLE user_permission_overrides (
    id            bigserial PRIMARY KEY,
    user_id       bigint      NOT NULL REFERENCES users (id),
    permission_id bigint      NOT NULL REFERENCES permissions (id),
    effect        varchar(10) NOT NULL CHECK (effect IN ('GRANT', 'REVOKE')),
    expires_at    timestamp with time zone,
    granted_by    bigint REFERENCES users (id),
    created_at    timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_permission_overrides_user_permission UNIQUE (user_id, permission_id)
);

-- Danh mục role, quyền và ma trận quyền mặc định (D-37)
INSERT INTO roles (code, name) VALUES
    ('STUDENT', 'Học sinh'),
    ('SUPPORT', 'Nhân viên hỗ trợ'),
    ('TEACHER', 'Giáo viên'),
    ('ACCOUNTANT', 'Kế toán'),
    ('DIRECTOR', 'Giám đốc'),
    ('EDITOR', 'Biên tập viên'),
    ('ADMIN', 'Quản trị viên');

INSERT INTO permissions (code, module, description) VALUES
    ('cms.content', 'cms', 'Soạn nội dung khóa học, bài học, bộ câu hỏi và câu hỏi'),
    ('report.view', 'report', 'Xem dashboard và báo cáo doanh thu, chi phí AI'),
    ('activation.manage', 'commerce', 'Tạo, xem và thu hồi link kích hoạt'),
    ('regrade.manage', 'exercise', 'Xem trước và xác nhận chấm lại bài cũ'),
    ('audit.view', 'audit', 'Xem nhật ký thao tác nội bộ'),
    ('rbac.manage', 'rbac', 'Quản lý vai trò, quyền và gán quyền cho nhân sự'),
    ('product.manage', 'commerce', 'Quản lý sản phẩm và giá'),
    ('order.view', 'commerce', 'Xem đơn hàng và xuất CSV đơn đã thanh toán'),
    ('student.view', 'student', 'Tra cứu học viên');

-- ADMIN: tất cả quyền
INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'ADMIN';

INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id
    FROM (VALUES
        ('DIRECTOR', 'report.view'), ('DIRECTOR', 'audit.view'), ('DIRECTOR', 'order.view'),
        ('ACCOUNTANT', 'report.view'), ('ACCOUNTANT', 'order.view'),
        ('SUPPORT', 'student.view'), ('SUPPORT', 'order.view'), ('SUPPORT', 'activation.manage'),
        ('EDITOR', 'cms.content'), ('EDITOR', 'regrade.manage'),
        ('TEACHER', 'regrade.manage'), ('TEACHER', 'student.view')
    ) AS m(role_code, perm_code)
    JOIN roles r ON r.code = m.role_code
    JOIN permissions p ON p.code = m.perm_code;

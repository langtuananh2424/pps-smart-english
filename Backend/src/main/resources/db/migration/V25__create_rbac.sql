-- FR-ADM-04, D-24: phân quyền dạng bảng. Role là tập quyền; nhân sự có nhiều role, được GRANT/REVOKE từng quyền có hạn tùy chọn.
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

-- Danh mục role và quyền ban đầu. Quyền lấy từ các chỗ đang kiểm tra quyền trong code; ma trận role chờ BQ-08.
INSERT INTO roles (code, name) VALUES
    ('STUDENT', 'Học sinh'),
    ('STAFF', 'Nhân viên'),
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
    ('rbac.manage', 'rbac', 'Quản lý vai trò, quyền và gán quyền cho nhân sự');

INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'ADMIN';
INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'cms.content' WHERE r.code = 'EDITOR';

-- Chuyển cột users.role cũ sang bảng liên kết (mã role cũ trùng mã mới), rồi bỏ cột.
INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, r.id FROM users u JOIN roles r ON r.code = u.role;
ALTER TABLE users DROP COLUMN role;

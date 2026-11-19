-- FR-SUP-03, D-37: thao tác thay đổi dữ liệu học viên (kích hoạt thủ công, cộng lượt bù, mở khóa tài khoản, gửi lại email xác thực).
-- Tra cứu chỉ cần student.view; ma trận role: Admin và Hỗ trợ.
INSERT INTO permissions (code, module, description) VALUES
    ('student.support', 'student', 'Kích hoạt khóa thủ công, cộng lượt bù, mở khóa tài khoản, gửi lại email xác thực cho học viên');

INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id
    FROM (VALUES ('ADMIN', 'student.support'), ('SUPPORT', 'student.support')) AS m(role_code, perm_code)
    JOIN roles r ON r.code = m.role_code
    JOIN permissions p ON p.code = m.perm_code;

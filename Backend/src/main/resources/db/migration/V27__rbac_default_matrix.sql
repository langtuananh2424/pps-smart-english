-- D-37, FR-ADM-04: ma trận quyền mặc định, thêm quyền product.manage / order.view / student.view, bỏ role STAFF (gộp vào SUPPORT).
INSERT INTO permissions (code, module, description) VALUES
    ('product.manage', 'commerce', 'Quản lý sản phẩm và giá'),
    ('order.view', 'commerce', 'Xem đơn hàng và xuất CSV đơn đã thanh toán'),
    ('student.view', 'student', 'Tra cứu học viên');

-- ADMIN: tất cả quyền (kể cả quyền mới)
INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
    WHERE r.code = 'ADMIN'
      AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- Các role nhân sự còn lại; NOT EXISTS để chạy lại không trùng khóa chính
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
    JOIN permissions p ON p.code = m.perm_code
    WHERE NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- STAFF -> SUPPORT: chuyển người đang giữ role (không trùng nếu đã có SUPPORT), rồi xóa role
INSERT INTO user_roles (user_id, role_id, granted_by, created_at)
    SELECT ur.user_id, sup.id, ur.granted_by, ur.created_at
    FROM user_roles ur
    JOIN roles st ON st.id = ur.role_id AND st.code = 'STAFF'
    JOIN roles sup ON sup.code = 'SUPPORT'
    WHERE NOT EXISTS (SELECT 1 FROM user_roles x WHERE x.user_id = ur.user_id AND x.role_id = sup.id);
DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE code = 'STAFF');
DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code = 'STAFF');
DELETE FROM roles WHERE code = 'STAFF';

-- FR-ADM-04, D-99, D-108, D-109: role Trợ giảng, Quản lý đào tạo (giáo vụ) và quyền cho phân hệ lớp học.
-- Quyền class.teach chỉ có hiệu lực trong lớp được phân công (NFR-SEC-05); kiểm theo lớp nằm ở service của phân hệ 11.
INSERT INTO roles (code, name) VALUES
    ('ASSISTANT', 'Trợ giảng'),
    ('TRAINING_MANAGER', 'Quản lý đào tạo');

INSERT INTO permissions (code, module, description) VALUES
    ('class.manage', 'class', 'Tạo lớp, xếp và chuyển học viên, phân công giáo viên, xếp lịch buổi học'),
    ('class.teach', 'class', 'Điểm danh, giao bài, chấm và nhận xét trong lớp được phân công'),
    ('report.teaching', 'report', 'Xem báo cáo số buổi dạy của giáo viên và trợ giảng');

INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id
    FROM (VALUES
        ('ADMIN', 'class.manage'), ('ADMIN', 'class.teach'), ('ADMIN', 'report.teaching'),
        ('TRAINING_MANAGER', 'class.manage'), ('TRAINING_MANAGER', 'report.teaching'), ('TRAINING_MANAGER', 'student.view'),
        ('TEACHER', 'class.teach'),
        ('ASSISTANT', 'class.teach'), ('ASSISTANT', 'student.view'),
        ('ACCOUNTANT', 'report.teaching')
    ) AS m(role_code, perm_code)
    JOIN roles r ON r.code = m.role_code
    JOIN permissions p ON p.code = m.perm_code;

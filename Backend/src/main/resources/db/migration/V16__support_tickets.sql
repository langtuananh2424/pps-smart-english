-- FR-SUP-02, D-16, BQ-09 (trả lời tạm 2026-11-19): ticket và chat học viên với nhân viên hỗ trợ.
-- Ba trạng thái: OPEN -> IN_PROGRESS (nhân viên nhận xử lý) -> CLOSED; học viên nhắn tiếp vào ticket đã đóng thì mở lại.
CREATE TABLE support_tickets (
    id              bigserial PRIMARY KEY,
    user_id         bigint       NOT NULL REFERENCES users (id),
    subject         varchar(200) NOT NULL,
    status          varchar(20)  NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'IN_PROGRESS', 'CLOSED')),
    assigned_to     bigint REFERENCES users (id),
    last_message_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at       timestamp with time zone,
    created_at      timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_support_tickets_user ON support_tickets (user_id, id DESC);
CREATE INDEX idx_support_tickets_status ON support_tickets (status, id DESC);

CREATE TABLE support_messages (
    id         bigserial PRIMARY KEY,
    ticket_id  bigint      NOT NULL REFERENCES support_tickets (id),
    sender_id  bigint      NOT NULL REFERENCES users (id),
    from_staff boolean     NOT NULL,
    body       varchar(2000) NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_support_messages_ticket ON support_messages (ticket_id, id);

-- Cấu hình giờ làm việc do admin nhập (BQ-09): một dòng; chưa cài thì coi như luôn trong giờ và không hiện gợi ý.
CREATE TABLE support_settings (
    id          integer PRIMARY KEY CHECK (id = 1),
    timezone    varchar(64),
    eta_message varchar(300),
    zalo_url    varchar(300),
    hotline     varchar(50)
);
INSERT INTO support_settings (id) VALUES (1);

-- day_of_week 1 = thứ Hai .. 7 = Chủ nhật; một ngày có thể có nhiều khung giờ.
CREATE TABLE support_hours (
    id          bigserial PRIMARY KEY,
    day_of_week integer NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    open_time   time    NOT NULL,
    close_time  time    NOT NULL,
    CONSTRAINT ck_support_hours_range CHECK (open_time < close_time)
);

INSERT INTO permissions (code, module, description) VALUES
    ('ticket.manage', 'support', 'Xem, nhận xử lý, trả lời và đóng ticket hỗ trợ học viên'),
    ('support.configure', 'support', 'Cài giờ làm việc, thời gian phản hồi dự kiến, Zalo và hotline của hỗ trợ');

INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id
    FROM (VALUES ('ADMIN', 'ticket.manage'), ('ADMIN', 'support.configure'), ('SUPPORT', 'ticket.manage')) AS m(role_code, perm_code)
    JOIN roles r ON r.code = m.role_code
    JOIN permissions p ON p.code = m.perm_code;

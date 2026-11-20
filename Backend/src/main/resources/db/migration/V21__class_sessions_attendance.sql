-- FR-SES-01..04, D-100, D-106, D-108, D-124: buổi học nhóm/kèm 1-1, điểm danh tay có lịch sử sửa, cấu hình mốc nhắc lịch.
CREATE TABLE class_sessions (
    id          bigserial PRIMARY KEY,
    class_id    bigint       NOT NULL REFERENCES classes (id),
    kind        varchar(10)  NOT NULL CHECK (kind IN ('GROUP', 'TUTORING')),
    student_id  bigint       REFERENCES users (id), -- chỉ buổi kèm 1-1
    mode        varchar(10)  NOT NULL CHECK (mode IN ('ONLINE', 'OFFLINE')),
    starts_at   timestamp with time zone NOT NULL,
    ends_at     timestamp with time zone NOT NULL,
    meeting_url varchar(500), -- buổi online do người tạo dán (BQ-16)
    taught_by   bigint       NOT NULL REFERENCES users (id),
    status      varchar(10)  NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'CANCELLED')),
    reminded_at timestamp with time zone, -- đã gửi nhắc lịch; đổi giờ thì xóa để nhắc lại
    created_by  bigint       NOT NULL REFERENCES users (id),
    created_at  timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_class_sessions_time CHECK (ends_at > starts_at),
    CONSTRAINT ck_class_sessions_tutoring CHECK ((kind = 'TUTORING') = (student_id IS NOT NULL))
);
CREATE INDEX idx_class_sessions_class ON class_sessions (class_id, starts_at);
CREATE INDEX idx_class_sessions_reminder ON class_sessions (status, starts_at);
CREATE INDEX idx_class_sessions_taught ON class_sessions (taught_by, starts_at);

CREATE TABLE attendance_marks (
    id         bigserial PRIMARY KEY,
    session_id bigint      NOT NULL REFERENCES class_sessions (id),
    user_id    bigint      NOT NULL REFERENCES users (id),
    status     varchar(10) NOT NULL CHECK (status IN ('PRESENT', 'ABSENT', 'LATE', 'EXCUSED')),
    marked_by  bigint      NOT NULL REFERENCES users (id),
    marked_at  timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_attendance_session_user UNIQUE (session_id, user_id)
);

-- Mỗi lần sửa điểm danh: người sửa, thời điểm, giá trị cũ (D-124). Lần điểm danh đầu tiên không ghi ở đây.
CREATE TABLE attendance_changes (
    id         bigserial PRIMARY KEY,
    session_id bigint      NOT NULL REFERENCES class_sessions (id),
    user_id    bigint      NOT NULL REFERENCES users (id),
    old_status varchar(10) NOT NULL,
    new_status varchar(10) NOT NULL,
    changed_by bigint      NOT NULL REFERENCES users (id),
    changed_at timestamp with time zone NOT NULL
);
CREATE INDEX idx_attendance_changes_session ON attendance_changes (session_id, id);

-- Một dòng id = 1: số phút nhắc trước giờ học do admin cài (D-124), mặc định 60.
CREATE TABLE class_settings (
    id               integer PRIMARY KEY CHECK (id = 1),
    reminder_minutes integer NOT NULL DEFAULT 60 CHECK (reminder_minutes BETWEEN 5 AND 10080)
);
INSERT INTO class_settings (id, reminder_minutes) VALUES (1, 60);

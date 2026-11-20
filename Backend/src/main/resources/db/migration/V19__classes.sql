-- FR-CLS-01..04, D-98, D-117, D-120: khóa có lớp, lớp, phân công giáo viên và học viên của lớp. Chỉ thêm bảng/cột mới.
ALTER TABLE courses ADD COLUMN delivery_mode varchar(20) NOT NULL DEFAULT 'SELF_PACED'
    CHECK (delivery_mode IN ('SELF_PACED', 'CLASS_BASED'));

CREATE TABLE classes (
    id         bigserial PRIMARY KEY,
    course_id  bigint       NOT NULL REFERENCES courses (id),
    name       varchar(150) NOT NULL,
    starts_on  date         NOT NULL,
    ends_on    date         NOT NULL,
    mode       varchar(10)  NOT NULL CHECK (mode IN ('ONLINE', 'OFFLINE')),
    capacity   integer      NOT NULL CHECK (capacity > 0),
    status     varchar(10)  NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLOSED')),
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_classes_dates CHECK (ends_on >= starts_on)
);
CREATE INDEX idx_classes_course ON classes (course_id, id DESC);

-- Phân công giáo viên chính (TEACHER) và trợ giảng (ASSISTANT). Mỗi lớp một TEACHER chưa bị thu hồi: service giữ bằng khóa dòng lớp.
-- revoked_at: thu hồi quyền ngay lập tức (đổi người); assigned_from/to là khoảng ngày phụ trách.
CREATE TABLE class_staff (
    id            bigserial PRIMARY KEY,
    class_id      bigint      NOT NULL REFERENCES classes (id),
    user_id       bigint      NOT NULL REFERENCES users (id),
    role          varchar(10) NOT NULL CHECK (role IN ('TEACHER', 'ASSISTANT')),
    assigned_from date        NOT NULL,
    assigned_to   date,
    revoked_at    timestamp with time zone,
    created_at    timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_class_staff_class ON class_staff (class_id);
CREATE INDEX idx_class_staff_user ON class_staff (user_id);

-- Một bản ghi mỗi lần học viên ở trong một lớp, giữ lịch sử xếp, chuyển, rút.
-- live_key = 'khóa:học viên' chỉ khi ACTIVE (NULL ở các trạng thái khác) nên unique chặn hai lớp đang học cùng một khóa kể cả khi đua nhau.
CREATE TABLE class_members (
    id         bigserial PRIMARY KEY,
    class_id   bigint      NOT NULL REFERENCES classes (id),
    course_id  bigint      NOT NULL REFERENCES courses (id),
    user_id    bigint      NOT NULL REFERENCES users (id),
    status     varchar(12) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'TRANSFERRED', 'WITHDRAWN', 'COMPLETED')),
    live_key   varchar(45),
    joined_at  timestamp with time zone NOT NULL,
    left_at    timestamp with time zone,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_class_members_live UNIQUE (live_key)
);
CREATE INDEX idx_class_members_class ON class_members (class_id, status);
CREATE INDEX idx_class_members_user ON class_members (user_id, id DESC);

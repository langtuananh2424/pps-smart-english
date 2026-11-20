-- FR-CLS-05..07, D-122, D-124: ngưỡng đánh dấu học viên tụt lại theo khóa, mở sớm unit, nhật ký xuất báo cáo số buổi dạy.

-- Chưa có dòng cho khóa thì dùng giá trị mặc định của service (cùng các số dưới đây).
CREATE TABLE course_lag_thresholds (
    course_id          bigint PRIMARY KEY REFERENCES courses (id),
    overdue_assignments integer NOT NULL DEFAULT 1 CHECK (overdue_assignments >= 1),
    absences           integer NOT NULL DEFAULT 3 CHECK (absences >= 1),
    units_behind       integer NOT NULL DEFAULT 1 CHECK (units_behind >= 1),
    min_avg_percent    integer NOT NULL DEFAULT 50 CHECK (min_avg_percent BETWEEN 0 AND 100),
    created_at         timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Mở sớm một unit (topic) cho cả lớp (user_id null) hoặc một học viên; giữ người mở, thời điểm. Thu hồi bằng revoked_at.
-- live_key = 'lớp:unit:học viên|ALL' khi chưa thu hồi (NULL khi thu hồi): unique chặn mở trùng kể cả khi đua nhau.
CREATE TABLE unlock_overrides (
    id         bigserial PRIMARY KEY,
    class_id   bigint NOT NULL REFERENCES classes (id),
    topic_id   bigint NOT NULL REFERENCES topics (id),
    user_id    bigint REFERENCES users (id),
    granted_by bigint NOT NULL REFERENCES users (id),
    granted_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    live_key   varchar(80),
    CONSTRAINT uq_unlock_overrides_live UNIQUE (live_key)
);
CREATE INDEX idx_unlock_overrides_class ON unlock_overrides (class_id, id DESC);

-- Mỗi lần xuất báo cáo số buổi dạy, để lần sau hiện các buổi bị sửa điểm danh kể từ đó (FR-CLS-07).
CREATE TABLE teaching_report_exports (
    id          bigserial PRIMARY KEY,
    exported_by bigint NOT NULL REFERENCES users (id),
    exported_at timestamp with time zone NOT NULL,
    from_date   date   NOT NULL,
    to_date     date   NOT NULL
);

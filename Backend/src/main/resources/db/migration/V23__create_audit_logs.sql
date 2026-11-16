-- FR-ADM-05: nhật ký thao tác của nhân sự nội bộ. Chỉ ghi thêm; bất biến trên PostgreSQL ở db/migration-pg/V24.
CREATE TABLE audit_logs (
    id           bigserial PRIMARY KEY,
    actor_id     bigint       NOT NULL REFERENCES users (id),
    action       varchar(100) NOT NULL,
    target_type  varchar(50)  NOT NULL,
    target_id    varchar(100) NOT NULL,
    before_value json,
    after_value  json,
    reason       varchar(500),
    created_at   timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_audit_logs_actor ON audit_logs (actor_id, id);
CREATE INDEX idx_audit_logs_target ON audit_logs (target_type, target_id, id);
CREATE INDEX idx_audit_logs_created ON audit_logs (created_at);

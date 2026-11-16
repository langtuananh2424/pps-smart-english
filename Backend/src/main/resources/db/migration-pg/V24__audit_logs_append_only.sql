-- Chỉ PostgreSQL: nhật ký không sửa, không xóa được (FR-ADM-05). H2 (test) không có trigger kiểu này nên ở đó chỉ có code không gọi update/delete.
CREATE OR REPLACE FUNCTION audit_logs_reject_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs chỉ ghi thêm, không sửa hoặc xóa';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_logs_append_only
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_reject_change();

-- JSON cột before/after nâng lên jsonb như các bảng khác.
ALTER TABLE audit_logs ALTER COLUMN before_value TYPE jsonb USING before_value::jsonb;
ALTER TABLE audit_logs ALTER COLUMN after_value TYPE jsonb USING after_value::jsonb;

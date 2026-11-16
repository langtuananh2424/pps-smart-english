-- Chỉ PostgreSQL. H2 (test) không có jsonb, trigger kiểu này và partial index, nên db/migration tạo cột json
-- và phần dưới đây nâng lên theo thiết kế.

-- JSON -> jsonb
ALTER TABLE questions ALTER COLUMN payload TYPE jsonb USING payload::jsonb;
ALTER TABLE questions ALTER COLUMN answer_key TYPE jsonb USING answer_key::jsonb;
ALTER TABLE student_answers ALTER COLUMN answer TYPE jsonb USING answer::jsonb;
ALTER TABLE student_answer_grading ALTER COLUMN result TYPE jsonb USING result::jsonb;
ALTER TABLE student_exercise_attempts ALTER COLUMN draft_answers TYPE jsonb USING draft_answers::jsonb;
ALTER TABLE payment_transactions ALTER COLUMN raw_payload TYPE jsonb USING raw_payload::jsonb;
ALTER TABLE writing_sessions ALTER COLUMN turns TYPE jsonb USING turns::jsonb;
ALTER TABLE ai_jobs ALTER COLUMN input TYPE jsonb USING input::jsonb;
ALTER TABLE ai_jobs ALTER COLUMN output TYPE jsonb USING output::jsonb;
ALTER TABLE regrade_items ALTER COLUMN old_result TYPE jsonb USING old_result::jsonb;
ALTER TABLE regrade_items ALTER COLUMN new_result TYPE jsonb USING new_result::jsonb;
ALTER TABLE speaking_submissions ALTER COLUMN result TYPE jsonb USING result::jsonb;
ALTER TABLE push_subscriptions ALTER COLUMN keys TYPE jsonb USING keys::jsonb;
ALTER TABLE notifications ALTER COLUMN payload TYPE jsonb USING payload::jsonb;
ALTER TABLE audit_logs ALTER COLUMN before_value TYPE jsonb USING before_value::jsonb;
ALTER TABLE audit_logs ALTER COLUMN after_value TYPE jsonb USING after_value::jsonb;
ALTER TABLE placement_attempts ALTER COLUMN draft_answers TYPE jsonb USING draft_answers::jsonb;
ALTER TABLE placement_attempts ALTER COLUMN answers TYPE jsonb USING answers::jsonb;
ALTER TABLE placement_attempts ALTER COLUMN skill_results TYPE jsonb USING skill_results::jsonb;

-- Nhật ký không sửa, không xóa được (FR-ADM-05)
CREATE OR REPLACE FUNCTION audit_logs_reject_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs chỉ ghi thêm, không sửa hoặc xóa';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_logs_append_only
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_reject_change();

CREATE INDEX idx_refresh_tokens_active_user ON refresh_tokens (user_id) WHERE revoked_at IS NULL;

-- Chỉ một đề kiểm tra đầu vào PUBLISHED (FR-ADM-06); service đã hạ đề cũ, index chặn race. CmsWrites đổi vi phạm thành 409 conflict.
CREATE UNIQUE INDEX uq_question_banks_one_published_placement ON question_banks ((true))
    WHERE purpose = 'PLACEMENT' AND status = 'PUBLISHED';

-- Q&A gợi ý khóa (D-55): json -> jsonb.
ALTER TABLE user_profiles ALTER COLUMN qa_answers TYPE jsonb USING qa_answers::jsonb;

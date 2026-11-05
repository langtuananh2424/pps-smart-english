-- Partial index: H2 (test) không hỗ trợ nên nằm ở thư mục riêng của PostgreSQL.
CREATE INDEX idx_refresh_tokens_active_user ON refresh_tokens (user_id) WHERE revoked_at IS NULL;

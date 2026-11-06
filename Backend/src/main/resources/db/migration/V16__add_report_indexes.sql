-- Báo cáo Admin (FR-ADM-03) lọc theo khoảng thời gian trên ba cột này.
CREATE INDEX idx_users_created_at ON users (created_at);
CREATE INDEX idx_orders_paid_at ON orders (paid_at);
CREATE INDEX idx_token_usage_created_at ON ai_grading_token_usage (created_at);

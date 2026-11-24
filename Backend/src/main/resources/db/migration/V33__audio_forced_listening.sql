-- #69 (FR-EX-10, D-135, D-136, D-138): âm thanh, đếm lượt nghe phía máy chủ và Nghe ép.
-- Chỉ thêm bảng và cột có mặc định/null được nên bản chạy cũ vẫn tương thích.

-- Chế độ phát (FREE nghe tua tự do, EXAM tự phát, LOCKED = Nghe ép) và số lần nghe của chế độ EXAM (null = mặc định cấu hình).
ALTER TABLE question_banks ADD COLUMN playback_mode varchar(10) NOT NULL DEFAULT 'FREE'
    CHECK (playback_mode IN ('FREE', 'EXAM', 'LOCKED'));
ALTER TABLE question_banks ADD COLUMN play_limit integer CHECK (play_limit IS NULL OR play_limit >= 1);

-- Audio và script của part. transcript_html đã làm sạch, KHÔNG bao giờ nằm trong đề trả cho học viên.
ALTER TABLE question_groups ADD COLUMN audio_asset_id bigint REFERENCES media_assets (id);
ALTER TABLE question_groups ADD COLUMN transcript_html text;
-- Script riêng của câu (chữ thuần); null = dùng script của nhóm.
ALTER TABLE questions ADD COLUMN transcript_excerpt text;

-- Luật Nghe ép: scope_key 'B:<bank>' = mặc định của bộ, 'A:<assignment>' = ghi đè khi giao bài; unique chặn trùng kể cả khi đua nhau.
CREATE TABLE forced_listening_rules (
    id              bigserial PRIMARY KEY,
    scope_key       varchar(30) NOT NULL,
    bank_id         bigint      NOT NULL REFERENCES question_banks (id),
    assignment_id   bigint      REFERENCES assignments (id),
    play_limit      integer     NOT NULL CHECK (play_limit >= 1),
    pass_pct_total  integer     NOT NULL CHECK (pass_pct_total BETWEEN 1 AND 100),
    hint_every_n    integer     NOT NULL CHECK (hint_every_n >= 1),
    wrong_threshold integer     NOT NULL CHECK (wrong_threshold >= 1),
    created_at      timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_forced_rules_scope UNIQUE (scope_key)
);
CREATE INDEX idx_forced_rules_bank ON forced_listening_rules (bank_id);

CREATE TABLE forced_listening_group_thresholds (
    id       bigserial PRIMARY KEY,
    rule_id  bigint  NOT NULL REFERENCES forced_listening_rules (id) ON DELETE CASCADE,
    group_id bigint  NOT NULL REFERENCES question_groups (id),
    pass_pct integer NOT NULL CHECK (pass_pct BETWEEN 1 AND 100),
    CONSTRAINT uq_forced_thresholds UNIQUE (rule_id, group_id)
);

-- Kết quả lượt làm: FULL như cũ, COUNT_ONLY chỉ trả số câu đúng. rule_id: luật đang áp lúc bắt đầu (null = mặc định cấu hình).
ALTER TABLE student_exercise_attempts ADD COLUMN result_mode varchar(12) NOT NULL DEFAULT 'FULL'
    CHECK (result_mode IN ('FULL', 'COUNT_ONLY'));
ALTER TABLE student_exercise_attempts ADD COLUMN rule_id bigint REFERENCES forced_listening_rules (id);
CREATE INDEX idx_attempts_user_bank_submitted ON student_exercise_attempts (user_id, bank_id, submitted_at);

-- Mỗi lần cấp link nghe là một dòng; UNIQUE chặn hai request đồng thời cùng chiếm một lượt.
-- expires_at: hết hạn link thì lượt nghe coi như đã xong dù client không báo; completed_at do client báo.
CREATE TABLE audio_plays (
    id           bigserial PRIMARY KEY,
    attempt_id   bigint NOT NULL REFERENCES student_exercise_attempts (id),
    group_id     bigint NOT NULL REFERENCES question_groups (id),
    play_no      integer NOT NULL CHECK (play_no >= 1),
    started_at   timestamp with time zone NOT NULL,
    expires_at   timestamp with time zone NOT NULL,
    completed_at timestamp with time zone,
    CONSTRAINT uq_audio_plays UNIQUE (attempt_id, group_id, play_no)
);

-- Học viên đã mở script của câu nào ở lượt nào (một dòng mỗi lượt và câu), để thống kê và giáo viên biết.
CREATE TABLE script_views (
    id          bigserial PRIMARY KEY,
    user_id     bigint NOT NULL REFERENCES users (id),
    question_id bigint NOT NULL REFERENCES questions (id),
    attempt_id  bigint NOT NULL REFERENCES student_exercise_attempts (id),
    viewed_at   timestamp with time zone NOT NULL,
    CONSTRAINT uq_script_views UNIQUE (attempt_id, question_id)
);
CREATE INDEX idx_script_views_user ON script_views (user_id, question_id);

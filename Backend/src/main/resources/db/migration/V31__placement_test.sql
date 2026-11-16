-- FR-TEST-01/02, FR-GOAL-03, D-23, D-39: kiểm tra đầu vào dùng lại question_banks/questions, không thuộc topic nào.
ALTER TABLE question_banks ADD COLUMN purpose varchar(20) NOT NULL DEFAULT 'COURSE';
ALTER TABLE question_banks ALTER COLUMN topic_id DROP NOT NULL;
ALTER TABLE question_banks ADD CONSTRAINT ck_question_banks_purpose CHECK (
    (purpose = 'COURSE' AND topic_id IS NOT NULL)
    OR (purpose = 'PLACEMENT' AND topic_id IS NULL AND mode = 'EXAM' AND time_limit_sec IS NOT NULL));

-- Kỹ năng của câu hỏi trong đề đầu vào; null với câu của khóa học.
ALTER TABLE questions ADD COLUMN skill varchar(20);
ALTER TABLE questions ADD CONSTRAINT ck_questions_skill CHECK (skill IS NULL OR skill IN ('LISTENING', 'READING', 'GRAMMAR'));

-- Dải band của khóa (đầu vào <= band < đầu ra) để gợi ý khóa; null = khóa không tham gia gợi ý.
ALTER TABLE courses ADD COLUMN band_from numeric(2, 1);
ALTER TABLE courses ADD COLUMN band_to numeric(2, 1);
ALTER TABLE courses ADD CONSTRAINT ck_courses_band CHECK (
    (band_from IS NULL AND band_to IS NULL)
    OR (band_from IS NOT NULL AND band_to IS NOT NULL AND band_from >= 0 AND band_to <= 9 AND band_from < band_to));

-- Một dòng duy nhất. max_retakes null = không cho làm lại (D-23).
CREATE TABLE placement_config (
    id          integer PRIMARY KEY CHECK (id = 1),
    max_retakes integer CHECK (max_retakes IS NULL OR max_retakes >= 0)
);
INSERT INTO placement_config (id, max_retakes) VALUES (1, NULL);

-- % đúng của kỹ năng -> band: lấy dòng có min_percent lớn nhất không vượt % đúng.
CREATE TABLE placement_band_rules (
    id          bigserial PRIMARY KEY,
    skill       varchar(20)   NOT NULL CHECK (skill IN ('LISTENING', 'READING', 'GRAMMAR')),
    min_percent integer       NOT NULL CHECK (min_percent BETWEEN 0 AND 100),
    band        numeric(2, 1) NOT NULL CHECK (band BETWEEN 0 AND 9),
    CONSTRAINT uq_placement_band_rules UNIQUE (skill, min_percent)
);

-- Lượt làm của khách hoặc học viên. Khách xác thực bằng guest_token_hash (SHA-256 của token), claim sẽ gắn user_id.
CREATE TABLE placement_attempts (
    id               bigserial PRIMARY KEY,
    bank_id          bigint      NOT NULL REFERENCES question_banks (id),
    user_id          bigint REFERENCES users (id),
    guest_token_hash varchar(64),
    started_at       timestamp with time zone NOT NULL,
    time_limit_sec   integer     NOT NULL,
    deadline_at      timestamp with time zone NOT NULL,
    draft_answers    json,
    answers          json,
    submitted_at     timestamp with time zone,
    skill_results    json,
    overall_band     numeric(2, 1),
    claimed_at       timestamp with time zone,
    CONSTRAINT ck_placement_attempts_owner CHECK (user_id IS NOT NULL OR guest_token_hash IS NOT NULL)
);
CREATE INDEX idx_placement_attempts_user ON placement_attempts (user_id, id DESC);
CREATE INDEX idx_placement_attempts_deadline ON placement_attempts (deadline_at);

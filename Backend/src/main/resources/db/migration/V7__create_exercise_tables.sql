-- Cột JSON dùng kiểu json (chạy được cả H2 lẫn PostgreSQL); db/migration-pg/V8 đổi sang jsonb trên PostgreSQL.
CREATE TABLE grammar_elements (
    id         bigserial PRIMARY KEY,
    code       varchar(40)  NOT NULL UNIQUE,
    name       varchar(200) NOT NULL,
    color_hex  varchar(7), -- null: chờ thiết kế Figma
    sort_order integer      NOT NULL DEFAULT 0,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO grammar_elements (code, name, sort_order) VALUES
    ('RELATIVE_CLAUSE',  'Mệnh đề quan hệ tính ngữ', 1),
    ('INVERSION',        'Câu đảo ngữ', 2),
    ('PASSIVE',          'Câu bị động', 3),
    ('DUMMY_SUBJECT',    'Chủ ngữ giả', 4),
    ('PARTICIPLE_CLAUSE', 'Mệnh đề phân từ', 5),
    ('NOUN_CLAUSE',      'Mệnh đề danh từ', 6),
    ('SEEM_LIKELY_TEND', 'Sử dụng "seem, be likely, tend to"', 7),
    ('OTHER_COMMON',     'Các cấu trúc quen thuộc khác', 8);

CREATE TABLE question_banks (
    id           bigserial PRIMARY KEY,
    topic_id     bigint       NOT NULL REFERENCES topics (id),
    lesson_id    bigint REFERENCES lessons (id), -- nút "Làm bài tập" sau bài giảng
    principle_id bigint REFERENCES principles (id),
    title        varchar(200) NOT NULL,
    is_trial     boolean      NOT NULL DEFAULT FALSE,
    created_at   timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_question_banks_topic ON question_banks (topic_id);

CREATE TABLE questions (
    id          bigserial PRIMARY KEY,
    bank_id     bigint      NOT NULL REFERENCES question_banks (id),
    type        varchar(30) NOT NULL CHECK (type IN (
                    'CONNECTIVE_MCQ', 'CONNECTIVE_MATCH', 'CONNECTIVE_CLOZE', 'CONNECTIVE_REWRITE',
                    'GRAMMAR_IDENTIFY', 'GRAMMAR_SPOT_FIX', 'GRAMMAR_BUILD', 'GRAMMAR_REWRITE',
                    'WRITING', 'SPEAKING')),
    prompt      text        NOT NULL,
    payload     json,
    answer_key  json, -- không bao giờ trả về client trước khi nộp
    explanation text,
    sort_order  integer     NOT NULL DEFAULT 0,
    created_at  timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_questions_bank_order ON questions (bank_id, sort_order);

CREATE TABLE question_choices (
    id          bigserial PRIMARY KEY,
    question_id bigint      NOT NULL REFERENCES questions (id),
    label       varchar(10) NOT NULL,
    content     text        NOT NULL,
    is_correct  boolean     NOT NULL DEFAULT FALSE,
    explanation text
);
CREATE INDEX idx_question_choices_question ON question_choices (question_id);

CREATE TABLE question_grammar_tags (
    question_id        bigint NOT NULL REFERENCES questions (id),
    grammar_element_id bigint NOT NULL REFERENCES grammar_elements (id),
    PRIMARY KEY (question_id, grammar_element_id)
);

CREATE TABLE student_exercise_attempts (
    id           bigserial PRIMARY KEY,
    user_id      bigint  NOT NULL REFERENCES users (id),
    bank_id      bigint  NOT NULL REFERENCES question_banks (id),
    started_at   timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at timestamp with time zone,
    score        integer,
    max_score    integer
);
-- Phân trang con trỏ theo id (tăng cùng started_at), nên chỉ mục theo (user_id, id DESC)
CREATE INDEX idx_attempts_user_id_desc ON student_exercise_attempts (user_id, id DESC);

CREATE TABLE student_answers (
    id          bigserial PRIMARY KEY,
    attempt_id  bigint NOT NULL REFERENCES student_exercise_attempts (id),
    question_id bigint NOT NULL REFERENCES questions (id),
    answer      json,
    is_correct  boolean, -- null: dạng chấm AI
    CONSTRAINT uq_student_answers_attempt_question UNIQUE (attempt_id, question_id)
);

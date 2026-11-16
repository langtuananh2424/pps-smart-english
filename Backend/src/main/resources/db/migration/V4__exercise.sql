-- Bài tập, chấm điểm và nội dung soạn sẵn cho Cam. Cột JSON dùng kiểu json (chạy được cả H2 lẫn PostgreSQL);
-- db/migration-pg/V8 đổi sang jsonb trên PostgreSQL.
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

-- mode: PRACTICE (được tạm dừng) hoặc EXAM (không tạm dừng), D-11, FR-EX-06.
-- purpose PLACEMENT: kiểm tra đầu vào, không thuộc topic nào (FR-TEST-01/02, D-23, D-39).
CREATE TABLE question_banks (
    id             bigserial PRIMARY KEY,
    topic_id       bigint REFERENCES topics (id),
    lesson_id      bigint REFERENCES lessons (id), -- nút "Làm bài tập" sau bài giảng
    principle_id   bigint REFERENCES principles (id),
    title          varchar(200) NOT NULL,
    is_trial       boolean      NOT NULL DEFAULT FALSE,
    created_at     timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status         varchar(20)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED')),
    time_limit_sec integer,
    mode           varchar(20)  NOT NULL DEFAULT 'PRACTICE',
    purpose        varchar(20)  NOT NULL DEFAULT 'COURSE',
    CONSTRAINT ck_question_banks_mode CHECK (mode IN ('PRACTICE', 'EXAM')),
    CONSTRAINT ck_question_banks_purpose CHECK (
        (purpose = 'COURSE' AND topic_id IS NOT NULL)
        OR (purpose = 'PLACEMENT' AND topic_id IS NULL AND mode = 'EXAM' AND time_limit_sec IS NOT NULL))
);
CREATE INDEX idx_question_banks_topic ON question_banks (topic_id);

ALTER TABLE courses ADD CONSTRAINT courses_exam_bank_id_fkey FOREIGN KEY (exam_bank_id) REFERENCES question_banks (id);

-- answer_version tăng mỗi lần payload, answer_key hoặc lựa chọn thật sự đổi (trừ dạng WRITING).
-- skill: kỹ năng của câu trong đề đầu vào; null với câu của khóa học.
CREATE TABLE questions (
    id             bigserial PRIMARY KEY,
    bank_id        bigint      NOT NULL REFERENCES question_banks (id),
    type           varchar(30) NOT NULL CHECK (type IN (
                       'CONNECTIVE_MCQ', 'CONNECTIVE_MATCH', 'CONNECTIVE_CLOZE', 'CONNECTIVE_REWRITE',
                       'GRAMMAR_IDENTIFY', 'GRAMMAR_SPOT_FIX', 'GRAMMAR_BUILD', 'GRAMMAR_REWRITE',
                       'WRITING', 'SPEAKING')),
    prompt         text        NOT NULL,
    payload        json,
    answer_key     json, -- không bao giờ trả về client trước khi nộp
    explanation    text,
    sort_order     integer     NOT NULL DEFAULT 0,
    created_at     timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    answer_version integer     NOT NULL DEFAULT 1,
    skill          varchar(20),
    CONSTRAINT ck_questions_skill CHECK (skill IS NULL OR skill IN ('LISTENING', 'READING', 'GRAMMAR'))
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

-- FR-AI-06, FR-AI-07, D-28: gợi ý theo câu và hỏi đáp theo bộ câu hỏi, soạn sẵn; lúc chạy không gọi AI.
CREATE TABLE question_hints (
    id          bigserial PRIMARY KEY,
    question_id bigint       NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    sort_order  integer      NOT NULL DEFAULT 0,
    content     varchar(500) NOT NULL,
    detail      text
);
CREATE INDEX idx_question_hints_question ON question_hints (question_id, sort_order);

CREATE TABLE bank_faqs (
    id         bigserial PRIMARY KEY,
    bank_id    bigint       NOT NULL REFERENCES question_banks (id) ON DELETE CASCADE,
    sort_order integer      NOT NULL DEFAULT 0,
    question   varchar(300) NOT NULL,
    answer     text         NOT NULL
);
CREATE INDEX idx_bank_faqs_bank ON bank_faqs (bank_id, sort_order);

-- Đồng hồ của lượt làm: time_used_sec là thời gian đã chạy trước các lần tạm dừng; resumed_at là lúc bắt đầu
-- đoạn đang chạy (null khi tạm dừng). draft_answers: đáp án đã lưu, máy chủ nộp khi hết giờ.
-- score là tổng credit nên có thể lẻ; max_score vẫn là số câu (nguyên).
CREATE TABLE student_exercise_attempts (
    id             bigserial PRIMARY KEY,
    user_id        bigint  NOT NULL REFERENCES users (id),
    bank_id        bigint  NOT NULL REFERENCES question_banks (id),
    started_at     timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at   timestamp with time zone,
    score          numeric(8, 2),
    max_score      integer,
    time_limit_sec integer,
    time_used_sec  integer NOT NULL DEFAULT 0,
    resumed_at     timestamp with time zone,
    draft_answers  json
);
-- Phân trang con trỏ theo id (tăng cùng started_at), nên chỉ mục theo (user_id, id DESC)
CREATE INDEX idx_attempts_user_id_desc ON student_exercise_attempts (user_id, id DESC);

-- credit: điểm câu 0..1 (4 chữ số thập phân), null khi câu chưa chấm (dạng AI), D-44, FR-EX-02
CREATE TABLE student_answers (
    id          bigserial PRIMARY KEY,
    attempt_id  bigint NOT NULL REFERENCES student_exercise_attempts (id),
    question_id bigint NOT NULL REFERENCES questions (id),
    answer      json,
    is_correct  boolean, -- null: dạng chấm AI
    credit      numeric(5, 4),
    CONSTRAINT uq_student_answers_attempt_question UNIQUE (attempt_id, question_id)
);

-- Kết quả chấm từng câu trả lời: RULE (luật, miễn phí) hoặc AI
CREATE TABLE student_answer_grading (
    id         bigserial PRIMARY KEY,
    answer_id  bigint      NOT NULL REFERENCES student_answers (id),
    grader     varchar(10) NOT NULL CHECK (grader IN ('RULE', 'AI')),
    score      integer     NOT NULL,
    result     json,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_answer_grading_answer_grader UNIQUE (answer_id, grader)
);

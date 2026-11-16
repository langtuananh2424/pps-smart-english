-- Danh mục khóa học và tiến độ bài giảng. courses.exam_bank_id (FK sang question_banks) thêm ở V4.
-- band_from/band_to: dải band của khóa (đầu vào <= band < đầu ra) để gợi ý khóa; null = không tham gia gợi ý.
CREATE TABLE principles (
    id         bigserial PRIMARY KEY,
    code       varchar(20)  NOT NULL UNIQUE,
    name       varchar(200) NOT NULL,
    status     varchar(20)  NOT NULL DEFAULT 'PLANNED' CHECK (status IN ('ACTIVE', 'PLANNED')),
    sort_order integer      NOT NULL DEFAULT 0,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Lượt AI định kỳ theo khóa (D-21: mặc định 20 lượt/tuần); bài thi cuối khóa (FR-EXAM-02, D-46)
CREATE TABLE courses (
    id                  bigserial PRIMARY KEY,
    slug                varchar(120) NOT NULL UNIQUE,
    title               varchar(200) NOT NULL,
    description         text,
    target_type         varchar(30),
    status              varchar(20)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED')),
    created_at          timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ai_period           varchar(10)  NOT NULL DEFAULT 'WEEK',
    ai_period_quota     integer      NOT NULL DEFAULT 20,
    exam_bank_id        bigint,
    exam_unlock_percent integer      NOT NULL DEFAULT 80,
    exam_retakes        integer      NOT NULL DEFAULT 0,
    band_from           numeric(2, 1),
    band_to             numeric(2, 1),
    CONSTRAINT ck_courses_ai_period CHECK (ai_period IN ('WEEK', 'MONTH')),
    CONSTRAINT ck_courses_ai_period_quota CHECK (ai_period_quota >= 0),
    CONSTRAINT ck_courses_exam_unlock_percent CHECK (exam_unlock_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_courses_exam_retakes CHECK (exam_retakes >= 0),
    -- nhiều NULL vẫn hợp lệ; một bộ đề chỉ làm bài thi của một khóa
    CONSTRAINT uq_courses_exam_bank UNIQUE (exam_bank_id),
    CONSTRAINT ck_courses_band CHECK (
        (band_from IS NULL AND band_to IS NULL)
        OR (band_from IS NOT NULL AND band_to IS NOT NULL AND band_from >= 0 AND band_to <= 9 AND band_from < band_to))
);

CREATE TABLE course_levels (
    id         bigserial PRIMARY KEY,
    course_id  bigint       NOT NULL REFERENCES courses (id),
    code       varchar(20)  NOT NULL,
    title      varchar(200) NOT NULL,
    sort_order integer      NOT NULL DEFAULT 0,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status     varchar(20)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT uq_course_levels_course_code UNIQUE (course_id, code)
);
CREATE INDEX idx_course_levels_course ON course_levels (course_id);

CREATE TABLE topics (
    id            bigserial PRIMARY KEY,
    level_id      bigint       NOT NULL REFERENCES course_levels (id),
    title         varchar(200) NOT NULL,
    sort_order    integer      NOT NULL DEFAULT 0,
    is_free_trial boolean      NOT NULL DEFAULT FALSE,
    created_at    timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status        varchar(20)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED'))
);
CREATE INDEX idx_topics_level ON topics (level_id);

CREATE TABLE lessons (
    id           bigserial PRIMARY KEY,
    topic_id     bigint       NOT NULL REFERENCES topics (id),
    principle_id bigint REFERENCES principles (id),
    type         varchar(30)  NOT NULL CHECK (type IN ('CONNECTIVE_VIDEO', 'REFLEX_VIDEO')),
    title        varchar(200) NOT NULL,
    video_source varchar(20)  CHECK (video_source IN ('STORAGE', 'YOUTUBE')), -- null: chưa upload
    video_ref    varchar(500),
    duration_sec integer,
    sort_order   integer      NOT NULL DEFAULT 0,
    status       varchar(20)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED')),
    created_at   timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_lessons_topic ON lessons (topic_id);
CREATE INDEX idx_lessons_principle ON lessons (principle_id);

CREATE TABLE lesson_progress (
    id               bigserial PRIMARY KEY,
    user_id          bigint  NOT NULL REFERENCES users (id),
    lesson_id        bigint  NOT NULL REFERENCES lessons (id),
    watched_sec      integer NOT NULL DEFAULT 0 CHECK (watched_sec >= 0),
    max_position_sec integer NOT NULL DEFAULT 0 CHECK (max_position_sec >= 0),
    completed_at     timestamp with time zone,
    created_at       timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_lesson_progress_user_lesson UNIQUE (user_id, lesson_id)
);
CREATE INDEX idx_lesson_progress_lesson ON lesson_progress (lesson_id);

CREATE TABLE principles (
    id         bigserial PRIMARY KEY,
    code       varchar(20)  NOT NULL UNIQUE,
    name       varchar(200) NOT NULL,
    status     varchar(20)  NOT NULL DEFAULT 'PLANNED' CHECK (status IN ('ACTIVE', 'PLANNED')),
    sort_order integer      NOT NULL DEFAULT 0,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE courses (
    id          bigserial PRIMARY KEY,
    slug        varchar(120) NOT NULL UNIQUE,
    title       varchar(200) NOT NULL,
    description text,
    target_type varchar(30),
    status      varchar(20)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED')),
    created_at  timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE course_levels (
    id         bigserial PRIMARY KEY,
    course_id  bigint       NOT NULL REFERENCES courses (id),
    code       varchar(20)  NOT NULL,
    title      varchar(200) NOT NULL,
    sort_order integer      NOT NULL DEFAULT 0,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_course_levels_course_code UNIQUE (course_id, code)
);

CREATE TABLE topics (
    id            bigserial PRIMARY KEY,
    level_id      bigint       NOT NULL REFERENCES course_levels (id),
    title         varchar(200) NOT NULL,
    sort_order    integer      NOT NULL DEFAULT 0,
    is_free_trial boolean      NOT NULL DEFAULT FALSE,
    created_at    timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
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
CREATE INDEX idx_course_levels_course ON course_levels (course_id);

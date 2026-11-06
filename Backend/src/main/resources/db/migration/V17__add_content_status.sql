-- CMS (FR-ADM-01): level, topic và bộ câu hỏi có trạng thái như course và lesson.
-- Dòng đang có thành PUBLISHED để nội dung hiện tại không biến mất; dòng tạo mới sau đó mặc định DRAFT.
ALTER TABLE course_levels ADD COLUMN status varchar(20) NOT NULL DEFAULT 'PUBLISHED' CHECK (status IN ('DRAFT', 'PUBLISHED'));
ALTER TABLE course_levels ALTER COLUMN status SET DEFAULT 'DRAFT';

ALTER TABLE topics ADD COLUMN status varchar(20) NOT NULL DEFAULT 'PUBLISHED' CHECK (status IN ('DRAFT', 'PUBLISHED'));
ALTER TABLE topics ALTER COLUMN status SET DEFAULT 'DRAFT';

ALTER TABLE question_banks ADD COLUMN status varchar(20) NOT NULL DEFAULT 'PUBLISHED' CHECK (status IN ('DRAFT', 'PUBLISHED'));
ALTER TABLE question_banks ALTER COLUMN status SET DEFAULT 'DRAFT';

-- D-69: tên yếu tố ngữ pháp bằng thuật ngữ tiếng Anh kèm màu nhãn Figma; D-70: câu hướng dẫn ngắn của câu hỏi.
UPDATE grammar_elements SET name = 'Relative clause',           color_hex = '#255a78' WHERE code = 'RELATIVE_CLAUSE';
UPDATE grammar_elements SET name = 'Inversion',                 color_hex = '#6d28d9' WHERE code = 'INVERSION';
UPDATE grammar_elements SET name = 'Passive voice',             color_hex = '#8b5308' WHERE code = 'PASSIVE';
UPDATE grammar_elements SET name = 'Dummy subject',             color_hex = '#08726c' WHERE code = 'DUMMY_SUBJECT';
UPDATE grammar_elements SET name = 'Participle clause',         color_hex = '#9b365c' WHERE code = 'PARTICIPLE_CLAUSE';
UPDATE grammar_elements SET name = 'Noun clause',               color_hex = '#454ba0' WHERE code = 'NOUN_CLAUSE';
UPDATE grammar_elements SET name = 'Seem / be likely / tend to', color_hex = '#166534' WHERE code = 'SEEM_LIKELY_TEND';
UPDATE grammar_elements SET name = 'Other common structures',   color_hex = '#535e70' WHERE code = 'OTHER_COMMON';

ALTER TABLE questions ADD COLUMN instruction varchar(300);

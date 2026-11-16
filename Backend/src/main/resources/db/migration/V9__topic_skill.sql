-- D-58: kỹ năng của topic (tùy chọn), dùng cho icon và chip lọc ở màn Bài giảng.
ALTER TABLE topics ADD COLUMN skill varchar(20)
    CHECK (skill IN ('LISTENING', 'READING', 'WRITING', 'SPEAKING', 'GRAMMAR_VOCAB'));

-- Chỉ PostgreSQL: gợi ý từ điển tìm theo tiền tố (word_key LIKE 'abc%'), cần text_pattern_ops để dùng được index với collation không phải C.
CREATE INDEX idx_dictionary_word_key_prefix ON dictionary_entries (word_key text_pattern_ops);

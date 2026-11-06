-- DỮ LIỆU DEMO, KHÔNG CHẠY Ở PRODUCTION. Chỉ nạp khi bật location classpath:db/demo (DEMO_FLYWAY_LOCATION).
-- Phụ thuộc R__demo_catalog (chạy trước theo thứ tự tên). Idempotent: bank khóa theo (topic, title), chỉ tạo khi chưa có.
DO $$
DECLARE
    free_topic bigint;
    locked_topic bigint;
    bid bigint;
    qid bigint;
BEGIN
    SELECT t.id INTO free_topic
    FROM topics t JOIN course_levels lv ON lv.id = t.level_id JOIN courses c ON c.id = lv.course_id
    WHERE c.slug = 'ielts-0-3' AND lv.code = 'L1' AND t.sort_order = 1;
    SELECT t.id INTO locked_topic
    FROM topics t JOIN course_levels lv ON lv.id = t.level_id JOIN courses c ON c.id = lv.course_id
    WHERE c.slug = 'ielts-0-3' AND lv.code = 'L1' AND t.sort_order = 2;

    -- Bank ở Topic học thử: đủ các dạng chấm tự động và hai dạng chấm AI
    IF free_topic IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM question_banks WHERE topic_id = free_topic AND title = 'Bài tập demo: từ nối và cấu trúc') THEN
        INSERT INTO question_banks (topic_id, lesson_id, title, is_trial)
        VALUES (free_topic,
                (SELECT id FROM lessons WHERE topic_id = free_topic AND sort_order = 1),
                'Bài tập demo: từ nối và cấu trúc', FALSE)
        RETURNING id INTO bid;

        -- 1. Trắc nghiệm: đáp án nằm ở question_choices
        INSERT INTO questions (bank_id, type, prompt, explanation, sort_order)
        VALUES (bid, 'CONNECTIVE_MCQ', 'She was very tired; ____, she finished the report.',
                '"Nevertheless" diễn tả sự đối lập: mệt nhưng vẫn hoàn thành.', 1)
        RETURNING id INTO qid;
        INSERT INTO question_choices (question_id, label, content, is_correct, explanation) VALUES
            (qid, 'A', 'Therefore', FALSE, '"Therefore" chỉ kết quả, không hợp với ý đối lập.'),
            (qid, 'B', 'Nevertheless', TRUE, 'Đúng: chỉ sự đối lập giữa hai vế.'),
            (qid, 'C', 'Moreover', FALSE, '"Moreover" bổ sung ý cùng chiều.'),
            (qid, 'D', 'For instance', FALSE, '"For instance" dùng để nêu ví dụ.');

        -- 2. Ghép hai vế câu
        INSERT INTO questions (bank_id, type, prompt, payload, answer_key, explanation, sort_order)
        VALUES (bid, 'CONNECTIVE_MATCH', 'Ghép hai vế để tạo câu đúng nghĩa.',
                '{"left":[{"id":"l1","text":"The road was closed"},{"id":"l2","text":"He studied hard"},{"id":"l3","text":"It rained all day"}],
                  "right":[{"id":"r3","text":"but the match still went ahead."},{"id":"r1","text":"so we took another route."},{"id":"r2","text":"because he wanted a scholarship."}]}',
                '{"pairs":{"l1":"r1","l2":"r2","l3":"r3"}}',
                '"so" nối kết quả, "because" nối lý do, "but" nối ý đối lập.', 2);

        -- 3. Điền từ nối vào đoạn
        INSERT INTO questions (bank_id, type, prompt, payload, answer_key, explanation, sort_order)
        VALUES (bid, 'CONNECTIVE_CLOZE', 'Điền từ nối thích hợp vào mỗi chỗ trống.',
                '{"text":"Many people prefer cities {b1} they offer more jobs. {b2}, living costs are high.","blanks":["b1","b2"]}',
                '{"blanks":{"b1":["because","since"],"b2":["however","nevertheless"]}}',
                'Chỗ 1 nêu lý do (because/since); chỗ 2 nêu ý đối lập (however/nevertheless).', 3);

        -- 4. Viết lại câu dùng từ nối: chấm luật trước (từ bắt buộc, câu mẫu chấp nhận), phần còn lại AI chấm
        INSERT INTO questions (bank_id, type, prompt, payload, answer_key, sort_order)
        VALUES (bid, 'CONNECTIVE_REWRITE', 'Viết lại hai câu thành một câu, dùng từ nối "although".',
                '{"sentence":"The test was difficult. Most students passed it.","requiredWord":"although"}',
                '{"acceptedAnswers":["Although the test was difficult, most students passed it.","Most students passed the test although it was difficult."]}',
                4);

        -- 5. Nhận diện cấu trúc (end là chỉ số token cuối, tính cả)
        INSERT INTO questions (bank_id, type, prompt, payload, answer_key, explanation, sort_order)
        VALUES (bid, 'GRAMMAR_IDENTIFY', 'Chọn cụm từ là mệnh đề quan hệ trong câu.',
                '{"tokens":["The","book","that","I","bought","yesterday","was","expensive","."]}',
                '{"spans":[{"start":2,"end":5,"element":"RELATIVE_CLAUSE"}]}',
                '"that I bought yesterday" bổ nghĩa cho "The book" nên là mệnh đề quan hệ.', 5)
        RETURNING id INTO qid;
        INSERT INTO question_grammar_tags (question_id, grammar_element_id)
        SELECT qid, id FROM grammar_elements WHERE code = 'RELATIVE_CLAUSE';

        -- 6. Tìm và sửa lỗi
        INSERT INTO questions (bank_id, type, prompt, payload, answer_key, explanation, sort_order)
        VALUES (bid, 'GRAMMAR_SPOT_FIX', 'Tìm lỗi trong câu bị động và sửa lại.',
                '{"tokens":["The","letter","was","write","by","Tom","."]}',
                '{"position":3,"corrections":["written"]}',
                'Câu bị động dùng "was + V3", nên "write" phải là "written".', 6)
        RETURNING id INTO qid;
        INSERT INTO question_grammar_tags (question_id, grammar_element_id)
        SELECT qid, id FROM grammar_elements WHERE code = 'PASSIVE';

        -- 7. Xếp khối thành câu (payload để lệch thứ tự đúng)
        INSERT INTO questions (bank_id, type, prompt, payload, answer_key, explanation, sort_order)
        VALUES (bid, 'GRAMMAR_BUILD', 'Sắp xếp các khối thành câu bị động hoàn chỉnh.',
                '{"blocks":[{"id":"b3","text":"by local workers"},{"id":"b1","text":"The new bridge"},{"id":"b4","text":"last year"},{"id":"b2","text":"was built"}]}',
                '{"order":["b1","b2","b3","b4"]}',
                'Chủ ngữ + was built + by + tác nhân + trạng ngữ thời gian.', 7)
        RETURNING id INTO qid;
        INSERT INTO question_grammar_tags (question_id, grammar_element_id)
        SELECT qid, id FROM grammar_elements WHERE code = 'PASSIVE';

        -- 8. Viết lại câu bằng cấu trúc yêu cầu: chấm luật trước, phần còn lại AI chấm
        INSERT INTO questions (bank_id, type, prompt, payload, answer_key, sort_order)
        VALUES (bid, 'GRAMMAR_REWRITE', 'Viết lại câu bằng câu bị động.',
                '{"sentence":"People say that regular exercise improves memory.","requiredElement":"PASSIVE"}',
                '{"acceptedAnswers":["Regular exercise is said to improve memory.","It is said that regular exercise improves memory."]}',
                8);
    END IF;

    -- Bank demo đã có từ trước: bổ sung câu mẫu cho câu viết lại và thêm câu WRITING nếu thiếu
    SELECT id INTO bid FROM question_banks WHERE topic_id = free_topic AND title = 'Bài tập demo: từ nối và cấu trúc';
    IF bid IS NOT NULL THEN
        UPDATE questions SET answer_key = '{"acceptedAnswers":["Although the test was difficult, most students passed it.","Most students passed the test although it was difficult."]}'
        WHERE bank_id = bid AND type = 'CONNECTIVE_REWRITE' AND answer_key IS NULL;
        UPDATE questions SET answer_key = '{"acceptedAnswers":["Regular exercise is said to improve memory.","It is said that regular exercise improves memory."]}'
        WHERE bank_id = bid AND type = 'GRAMMAR_REWRITE' AND answer_key IS NULL;

        -- 9. Viết phát triển ý (chấm AI qua phiên viết): payload có đề và số từ yêu cầu
        IF NOT EXISTS (SELECT 1 FROM questions WHERE bank_id = bid AND type = 'WRITING') THEN
            INSERT INTO questions (bank_id, type, prompt, payload, sort_order)
            VALUES (bid, 'WRITING', 'Bạn nghĩ học trực tuyến có tốt hơn học ở lớp không? Hãy nêu ý kiến của bạn.',
                    '{"task":"Some people think online learning is better than learning in a classroom. Do you agree or disagree?","requiredWords":120}',
                    9);
        END IF;
    END IF;

    -- Bank ở Topic không học thử: học viên chưa mua sẽ nhận 403 bank_locked
    IF locked_topic IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM question_banks WHERE topic_id = locked_topic AND title = 'Bài tập demo: cần mở khóa') THEN
        INSERT INTO question_banks (topic_id, title, is_trial)
        VALUES (locked_topic, 'Bài tập demo: cần mở khóa', FALSE)
        RETURNING id INTO bid;

        INSERT INTO questions (bank_id, type, prompt, explanation, sort_order)
        VALUES (bid, 'CONNECTIVE_MCQ', 'He missed the bus; ____, he arrived late.', '"As a result" chỉ kết quả của việc lỡ xe buýt.', 1)
        RETURNING id INTO qid;
        INSERT INTO question_choices (question_id, label, content, is_correct) VALUES
            (qid, 'A', 'As a result', TRUE),
            (qid, 'B', 'In contrast', FALSE),
            (qid, 'C', 'Besides', FALSE);
    END IF;
END $$;

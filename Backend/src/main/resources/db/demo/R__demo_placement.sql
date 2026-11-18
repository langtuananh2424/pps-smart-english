-- DỮ LIỆU MẪU (D-88), KHÔNG CHẠY Ở PRODUCTION. Chỉ nạp khi bật location classpath:db/demo (DEMO_FLYWAY_LOCATION).
-- Đề, ngưỡng band và số lần làm lại dưới đây chỉ để chạy thử màn Kiểm tra đầu vào; đề thật do Phòng Đào tạo nhập qua CMS.
-- Idempotent: chỉ nạp khi chưa có bất kỳ đề PLACEMENT nào, nên không ghi đè đề do CMS tạo.
-- Chưa có câu LISTENING vì chưa có audio.
DO $$
DECLARE
    bid bigint;
    qid bigint;
    m jsonb;
    c jsonb;
    i int;
BEGIN
    IF EXISTS (SELECT 1 FROM question_banks WHERE purpose = 'PLACEMENT') THEN
        RETURN;
    END IF;

    INSERT INTO question_banks (topic_id, title, is_trial, status, mode, purpose, time_limit_sec)
    VALUES (NULL, 'Kiểm tra đầu vào (đề mẫu)', FALSE, 'PUBLISHED', 'EXAM', 'PLACEMENT', 900)
    RETURNING id INTO bid;

    -- Trắc nghiệm: o = thứ tự, correct = chỉ số (từ 0) của đáp án đúng
    FOR m IN SELECT * FROM jsonb_array_elements($j$[
      {"o":1,"skill":"READING","prompt":"Đọc đoạn văn rồi chọn đáp án đúng.\n\nMai usually walks to school because it is only ten minutes from her house. When it rains, her father drives her instead.\n\nHow does Mai go to school when it rains?",
       "choices":["She walks","Her father drives her","She takes the bus","She stays at home"],"correct":1,"exp":"Đoạn văn nói khi trời mưa thì bố đưa Mai đi bằng xe."},
      {"o":2,"skill":"READING","prompt":"Đọc đoạn văn rồi chọn đáp án đúng.\n\nMai usually walks to school because it is only ten minutes from her house. When it rains, her father drives her instead.\n\nWhy does Mai usually walk?",
       "choices":["She likes the rain","Her school is close","Her father is busy","She has no bike"],"correct":1,"exp":"Trường chỉ cách nhà mười phút đi bộ."},
      {"o":3,"skill":"READING","prompt":"Đọc đoạn văn rồi chọn đáp án đúng.\n\nMany companies now let employees work from home two days a week. Supporters say this saves time and money, but some managers worry that teamwork may suffer.\n\nWhat is the managers' concern?",
       "choices":["Employees earn too much","Offices are too small","Teamwork may get worse","Travel costs are high"],"correct":2,"exp":"Câu cuối: một số quản lý lo làm việc nhóm bị ảnh hưởng."},
      {"o":4,"skill":"READING","prompt":"Đọc đoạn văn rồi chọn đáp án đúng.\n\nMany companies now let employees work from home two days a week. Supporters say this saves time and money, but some managers worry that teamwork may suffer.\n\nIn this passage, \"suffer\" is closest in meaning to:",
       "choices":["improve","become worse","disappear","change names"],"correct":1,"exp":"\"Suffer\" ở đây là bị ảnh hưởng xấu, tức trở nên tệ hơn."},
      {"o":7,"skill":"GRAMMAR","prompt":"She ____ in Hanoi since 2020.",
       "choices":["lives","lived","has lived","is living"],"correct":2,"exp":"\"Since 2020\" đi với hiện tại hoàn thành."},
      {"o":8,"skill":"GRAMMAR","prompt":"If I ____ more time, I would learn another language.",
       "choices":["have","had","will have","would have"],"correct":1,"exp":"Câu điều kiện loại 2: if + quá khứ đơn, would + V."}
    ]$j$::jsonb) LOOP
        INSERT INTO questions (bank_id, type, prompt, explanation, sort_order, skill)
        VALUES (bid, 'CONNECTIVE_MCQ', m->>'prompt', m->>'exp', (m->>'o')::int, m->>'skill')
        RETURNING id INTO qid;
        i := 0;
        FOR c IN SELECT * FROM jsonb_array_elements(m->'choices') LOOP
            INSERT INTO question_choices (question_id, label, content, is_correct)
            VALUES (qid, chr(65 + i), c #>> '{}', i = (m->>'correct')::int);
            i := i + 1;
        END LOOP;
    END LOOP;

    -- Đọc: điền từ vào đoạn văn
    INSERT INTO questions (bank_id, type, prompt, payload, answer_key, explanation, sort_order, skill)
    VALUES (bid, 'CONNECTIVE_CLOZE', 'Đọc đoạn văn rồi điền từ còn thiếu vào mỗi chỗ trống.',
            '{"text":"Tom wakes up at six every day. He eats breakfast {b1} he goes to work by bus. In the evening he {b2} dinner with his family.","blanks":["b1","b2"],"labels":{"b1":"Từ nối chỉ trình tự","b2":"Động từ chia ở hiện tại đơn"}}',
            '{"blanks":{"b1":["and","then","before"],"b2":["has","eats"]}}',
            'Chỗ 1 nối hai việc nối tiếp nhau; chỗ 2 là động từ hiện tại đơn ngôi thứ ba.', 5, 'READING');

    -- Đọc: ghép hai vế
    INSERT INTO questions (bank_id, type, prompt, payload, answer_key, explanation, sort_order, skill)
    VALUES (bid, 'CONNECTIVE_MATCH', 'Ghép hai vế để tạo câu đúng nghĩa.',
            '{"left":[{"id":"l1","text":"She was tired"},{"id":"l2","text":"He missed the bus"},{"id":"l3","text":"We stayed home"}],
              "right":[{"id":"r3","text":"because it was snowing heavily."},{"id":"r1","text":"but she kept working."},{"id":"r2","text":"so he walked to work."}]}',
            '{"pairs":{"l1":"r1","l2":"r2","l3":"r3"}}',
            '"but" nối ý đối lập, "so" nối kết quả, "because" nối lý do.', 6, 'READING');

    -- Ngữ pháp: nhận diện cấu trúc (end là chỉ số token cuối, tính cả)
    INSERT INTO questions (bank_id, type, prompt, payload, answer_key, explanation, sort_order, skill)
    VALUES (bid, 'GRAMMAR_IDENTIFY', 'Chạm từ đầu rồi từ cuối của mệnh đề quan hệ.',
            '{"tokens":["The","woman","who","lives","next","door","is","a","doctor","."]}',
            '{"spans":[{"start":2,"end":5,"element":"RELATIVE_CLAUSE"}]}',
            '"who lives next door" bổ nghĩa cho "The woman".', 9, 'GRAMMAR')
    RETURNING id INTO qid;
    INSERT INTO question_grammar_tags (question_id, grammar_element_id)
    SELECT qid, id FROM grammar_elements WHERE code = 'RELATIVE_CLAUSE';

    -- Ngữ pháp: tìm và sửa lỗi
    INSERT INTO questions (bank_id, type, prompt, payload, answer_key, explanation, sort_order, skill)
    VALUES (bid, 'GRAMMAR_SPOT_FIX', 'Tìm lỗi trong câu bị động và sửa lại.',
            '{"tokens":["The","window","was","break","by","the","wind","."]}',
            '{"position":3,"corrections":["broken"]}',
            'Câu bị động dùng "was + V3", nên "break" phải là "broken".', 10, 'GRAMMAR')
    RETURNING id INTO qid;
    INSERT INTO question_grammar_tags (question_id, grammar_element_id)
    SELECT qid, id FROM grammar_elements WHERE code = 'PASSIVE';

    -- Ngữ pháp: xếp khối thành câu
    INSERT INTO questions (bank_id, type, prompt, payload, answer_key, explanation, sort_order, skill)
    VALUES (bid, 'GRAMMAR_BUILD', 'Sắp xếp các khối thành câu hoàn chỉnh.',
            '{"blocks":[{"id":"b3","text":"every morning"},{"id":"b1","text":"The students"},{"id":"b4","text":"in the library"},{"id":"b2","text":"read quietly"}]}',
            '{"order":["b1","b2","b4","b3"]}',
            'Chủ ngữ + động từ + nơi chốn + thời gian.', 11, 'GRAMMAR')
    RETURNING id INTO qid;
    INSERT INTO question_grammar_tags (question_id, grammar_element_id)
    SELECT qid, id FROM grammar_elements WHERE code = 'OTHER_COMMON';

    -- Ngữ pháp: điền từ
    INSERT INTO questions (bank_id, type, prompt, payload, answer_key, explanation, sort_order, skill)
    VALUES (bid, 'CONNECTIVE_CLOZE', 'Điền dạng đúng của động từ trong ngoặc.',
            '{"text":"By the time we arrived, the film {b1} (already / start). She suggested {b2} (go) by taxi.","blanks":["b1","b2"],"labels":{"b1":"Quá khứ hoàn thành","b2":"Dạng động từ sau suggest"}}',
            '{"blanks":{"b1":["had already started","had started"],"b2":["going"]}}',
            'Chỗ 1 dùng quá khứ hoàn thành; chỗ 2 sau "suggest" dùng V-ing.', 12, 'GRAMMAR');

    -- Bảng quy đổi % đúng -> band MẪU cho từng kỹ năng
    INSERT INTO placement_band_rules (skill, min_percent, band)
    SELECT s.skill, r.min_percent, r.band
    FROM (VALUES ('READING'), ('GRAMMAR')) AS s(skill)
    CROSS JOIN (VALUES (0, 0.0), (20, 2.0), (35, 3.0), (50, 4.0), (65, 5.0), (80, 5.5), (90, 6.0)) AS r(min_percent, band)
    ON CONFLICT (skill, min_percent) DO NOTHING;

    -- Cho làm lại tối đa 2 lần để thử luồng; mặc định của hệ thống là không cho làm lại (D-23)
    UPDATE placement_config SET max_retakes = 2 WHERE id = 1 AND max_retakes IS NULL;
END $$;

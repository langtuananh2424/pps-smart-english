-- #69 (D-143): bài giao chốt chế độ Nghe ép TẠI LÚC GIAO. Quyết định "làm lại không giới hạn", trạng thái theo lượt đầu và bảng
-- tiến độ Nghe ép dựa vào cờ này, không dựa vào chế độ hiện tại của bộ (bộ đổi chế độ sau đó không đổi bài đã giao).
ALTER TABLE assignments ADD COLUMN forced_listening boolean NOT NULL DEFAULT false;
-- Bài giao cả bộ đã có sẵn trên bộ đang ở chế độ Nghe ép coi như Nghe ép.
UPDATE assignments SET forced_listening = true WHERE question_id IS NULL
    AND bank_id IN (SELECT id FROM question_banks WHERE playback_mode = 'LOCKED');

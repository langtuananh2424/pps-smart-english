package com.pps.smartenglish.exercise.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** LETTER_ORDER: id khối chữ do server sinh ngẫu nhiên, không mang thứ tự, để không suy ra từ đúng qua id (client không đáng tin). */
public final class LetterIds {

    private static final SecureRandom RNG = new SecureRandom();

    private LetterIds() {}

    public static JsonNode scramble(QuestionType type, JsonNode payload) {
        return scramble(type, payload, null);
    }

    /**
     * Đã qua validator. Trả payload với id mới; kiểu khác hoặc payload lạ thì giữ nguyên. Khối chữ trùng chữ với khối cũ
     * (theo thứ tự xuất hiện, chữ lặp ghép lần lượt) giữ id cũ, vì bài nộp và nháp lưu theo id.
     */
    public static JsonNode scramble(QuestionType type, JsonNode payload, JsonNode old) {
        if (type != QuestionType.LETTER_ORDER || payload == null || !payload.path("letters").isArray()) {
            return payload;
        }
        ObjectNode out = payload.deepCopy();
        Set<String> used = new HashSet<>();
        Map<String, Deque<String>> reuse = new HashMap<>();
        if (old != null && old.path("letters").isArray()) {
            old.get("letters").forEach(o -> {
                if (o.hasNonNull("id")) {
                    reuse.computeIfAbsent(o.path("ch").asText(), k -> new ArrayDeque<>()).add(o.get("id").asText());
                    used.add(o.get("id").asText());
                }
            });
        }
        for (JsonNode l : (ArrayNode) out.get("letters")) {
            var kept = reuse.get(l.path("ch").asText());
            if (kept != null && !kept.isEmpty()) {
                ((ObjectNode) l).put("id", kept.poll());
                continue;
            }
            String id;
            do {
                id = Long.toString(RNG.nextLong(36L * 36 * 36 * 36 * 36 * 36), 36);
                id = "0".repeat(6 - id.length()) + id;
            } while (!used.add(id));
            ((ObjectNode) l).put("id", id);
        }
        return out;
    }
}

package com.pps.smartenglish.exercise.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

/** LETTER_ORDER: id khối chữ do server sinh ngẫu nhiên, không mang thứ tự, để không suy ra từ đúng qua id (client không đáng tin). */
public final class LetterIds {

    private static final SecureRandom RNG = new SecureRandom();

    private LetterIds() {}

    /** Đã qua validator. Trả payload với id mới; kiểu khác hoặc payload lạ thì giữ nguyên. */
    public static JsonNode scramble(QuestionType type, JsonNode payload) {
        if (type != QuestionType.LETTER_ORDER || payload == null || !payload.path("letters").isArray()) {
            return payload;
        }
        ObjectNode out = payload.deepCopy();
        Set<String> used = new HashSet<>();
        for (JsonNode l : (ArrayNode) out.get("letters")) {
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

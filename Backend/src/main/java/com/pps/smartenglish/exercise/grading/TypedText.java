package com.pps.smartenglish.exercise.grading;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Luật 4 (docs/ba/question-types-d154.md) cho mọi dạng gõ chữ: bỏ khoảng trắng thừa, không phân biệt hoa thường, so với danh sách
 * đáp án chấp nhận; maxWords đếm theo \s+. Chép chính tả (dropPunct) còn bỏ . , ! ? ; : trước khi so. Web phải dùng cùng luật.
 */
public final class TypedText {

    /** Trần độ dài câu trả lời được so (P0: không so chuỗi khổng lồ); dài hơn là sai. */
    public static final int MAX_CHARS = 2000;

    private static final Pattern SPACES = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern PUNCT = Pattern.compile("[.,!?;:]");

    private TypedText() {}

    /** Dạng dùng để so và đếm từ. */
    public static String form(String raw, boolean dropPunct) {
        String s = raw.toLowerCase(Locale.ROOT);
        if (dropPunct) {
            s = PUNCT.matcher(s).replaceAll("");
        }
        return SPACES.matcher(s).replaceAll(" ").strip();
    }

    /** Số từ của chuỗi đã qua form. */
    public static int words(String formed) {
        return formed.isEmpty() ? 0 : SPACES.split(formed).length;
    }

    /** answerKey {accepted:[...]}; payload.maxWords tùy chọn: dài hơn là sai dù chứa đáp án. */
    public static boolean matches(JsonNode payload, JsonNode key, JsonNode given, boolean dropPunct) {
        if (given == null || !given.isTextual() || given.asText().length() > MAX_CHARS) {
            return false;
        }
        String text = form(given.asText(), dropPunct);
        Long max = payload == null ? null : JsonValues.integer(payload.path("maxWords"));
        if (text.isEmpty() || (max != null && words(text) > max)) {
            return false;
        }
        for (JsonNode a : key.path("accepted")) {
            if (a.isTextual() && text.equals(form(a.asText(), dropPunct))) {
                return true;
            }
        }
        return false;
    }
}

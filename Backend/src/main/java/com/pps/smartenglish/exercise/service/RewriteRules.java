package com.pps.smartenglish.exercise.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.Question;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Chấm luật miễn phí cho câu viết lại trước khi tốn lượt AI (Q-06, Q-30): bỏ trống thì sai; thiếu từ nối bắt buộc
 * (payload.requiredWord, so nguyên từ, không phân biệt hoa thường) thì sai; khớp answer_key.acceptedAnswers thì đúng.
 * Còn lại mới cần AI.
 */
@Component
class RewriteRules {

    /** Trần độ dài một câu viết lại: chặn gửi văn bản dài để đội chi phí AI (NFR-SEC-03). */
    static final int MAX_CHARS = 1000;

    enum Verdict { WRONG, CORRECT, NEEDS_AI }

    /** feedback: lời nhận xét hiển thị; corrected: câu mẫu nếu biết (acceptedAnswers đầu tiên). */
    record Result(Verdict verdict, String feedback, String corrected) {}

    Result check(Question q, JsonNode answer) {
        String text = answer != null && answer.path("text").isTextual() ? answer.path("text").asText().strip() : "";
        String accepted = firstAccepted(q);
        if (text.isEmpty()) {
            return new Result(Verdict.WRONG, "Bạn chưa viết câu trả lời.", accepted);
        }
        if (text.length() > MAX_CHARS) {
            return new Result(Verdict.WRONG, "Câu trả lời quá dài.", accepted);
        }
        String required = q.getPayload() == null ? "" : q.getPayload().path("requiredWord").asText("");
        if (!required.isBlank() && !containsWord(text, required)) {
            return new Result(Verdict.WRONG, "Câu chưa dùng từ nối bắt buộc: " + required + ".", accepted);
        }
        if (q.getAnswerKey() != null) {
            String normalized = normalize(text);
            for (JsonNode a : q.getAnswerKey().path("acceptedAnswers")) {
                if (a.isTextual() && normalize(a.asText()).equals(normalized)) {
                    return new Result(Verdict.CORRECT, "Chính xác.", a.asText());
                }
            }
        }
        return new Result(Verdict.NEEDS_AI, null, accepted);
    }

    private static String firstAccepted(Question q) {
        if (q.getAnswerKey() == null) {
            return null;
        }
        for (JsonNode a : q.getAnswerKey().path("acceptedAnswers")) {
            if (a.isTextual()) {
                return a.asText();
            }
        }
        return null;
    }

    /** Chữ thường, gom khoảng trắng, bỏ dấu câu cuối. */
    static String normalize(String s) {
        return s.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").replaceAll("[\\p{Punct}\\s]+$", "");
    }

    static boolean containsWord(String text, String word) {
        return Pattern.compile("(?<![\\p{L}\\p{N}'])" + Pattern.quote(word.strip()) + "(?![\\p{L}\\p{N}'])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text).find();
    }
}

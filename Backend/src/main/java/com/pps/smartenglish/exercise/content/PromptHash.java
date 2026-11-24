package com.pps.smartenglish.exercise.content;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.jsoup.Jsoup;

/**
 * Dấu vân tay để gợi ý câu trùng (D-133): SHA-256 của chữ đã chuẩn hóa (bỏ thẻ, hạ chữ thường, gộp khoảng trắng).
 * Nhiều dạng để nội dung thật trong payload (đoạn điền, câu cần sửa, các khối) còn prompt chỉ là lời hướng dẫn chung,
 * nên chữ trong payload cũng được tính; nếu không mọi câu cùng dạng sẽ trùng nhau.
 */
public final class PromptHash {

    private PromptHash() {}

    public static String of(String prompt, JsonNode payload) {
        String text = normalize((prompt == null ? "" : prompt) + " " + payloadText(payload));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256 luôn có trong JRE
        }
    }

    static String normalize(String s) {
        String plain = Jsoup.parse(s).text();
        return Normalizer.normalize(plain, Normalizer.Form.NFC).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }

    /** Chuỗi trong payload: trường chữ theo tên trường; mảng thì sắp xếp để thứ tự trộn khối không đổi dấu vân tay. */
    private static String payloadText(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        payload.fieldNames().forEachRemaining(name -> {
            JsonNode v = payload.get(name);
            if (v.isTextual()) {
                parts.add(v.asText());
            } else if (v.isArray()) {
                List<String> items = new ArrayList<>();
                v.forEach(i -> items.add(i.isTextual() ? i.asText() : i.path("text").asText("")));
                items.sort(null);
                parts.addAll(items);
            }
        });
        return String.join(" ", parts);
    }
}

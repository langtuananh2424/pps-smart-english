package com.pps.smartenglish.exercise.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.LayoutKind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chỗ trống và id lựa chọn có trong một layout đã lưu. slots giữ thứ tự và cả mục lặp (để validator bắt trùng);
 * ids là id option/word, viết thường vì grader so không phân biệt hoa thường.
 */
public record LayoutIndex(LayoutKind kind, List<String> slots, Set<String> ids) {

    /** Id của chỗ trống và lựa chọn: ngắn, không ký tự lạ. */
    public static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{1,40}");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(" + ID.pattern() + ")\\}");

    public static LayoutIndex of(JsonNode layout) {
        LayoutKind kind = LayoutKind.valueOf(layout.path("kind").asText());
        List<String> slots = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        switch (kind) {
            case OPTIONS, MAP -> ids(layout.path("options"), ids);
            case TEXT -> scan(layout.path("html").asText(), slots);
            case TEXT_BANK -> {
                scan(layout.path("html").asText(), slots);
                ids(layout.path("words"), ids);
            }
            case NOTE -> layout.path("lines").forEach(l -> scan(l.path("html").asText(), slots));
            case TABLE -> layout.path("rows").forEach(r -> r.forEach(c -> scan(c.asText(), slots)));
            case FLOW -> layout.path("steps").forEach(s -> scan(s.asText(), slots));
            case FORM -> layout.path("rows").forEach(r -> add(r.path("slot"), slots));
            case DIAGRAM -> layout.path("points").forEach(p -> add(p.path("slot"), slots));
        }
        return new LayoutIndex(kind, slots, ids);
    }

    private static void scan(String text, List<String> slots) {
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            slots.add(m.group(1));
        }
    }

    private static void add(JsonNode n, List<String> slots) {
        if (n.isTextual()) {
            slots.add(n.asText());
        }
    }

    private static void ids(JsonNode arr, Set<String> ids) {
        arr.forEach(o -> ids.add(o.path("id").asText().toLowerCase(Locale.ROOT)));
    }
}

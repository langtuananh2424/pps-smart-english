package com.pps.smartenglish.lessoncontent;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Một dòng JSONL của wiktextract (kaikki) thành một mục từ điển Anh-Việt (D-175).
 * vi: nghĩa lấy từ glosses tiếng Việt của vi.wiktionary; en: nghĩa lấy từ bản dịch "vi" của en.wiktionary (không có ví dụ).
 */
final class WiktionaryEntryMapper {

    record Row(String word, String pos, String meaningVi, String example) {}

    static final int WORD_MAX = 100;
    private static final int POS_MAX = 30;
    private static final int TEXT_MAX = 500;
    private static final int GLOSSES_MAX = 3;

    private WiktionaryEntryMapper() {}

    static Optional<Row> map(JsonNode line, String edition) {
        if (!"en".equals(line.path("lang_code").asText())) {
            return Optional.empty();
        }
        String word = line.path("word").asText("").strip();
        if (word.isEmpty() || word.length() > WORD_MAX) {
            return Optional.empty();
        }
        boolean vi = "vi".equals(edition);
        String meaning = vi ? glosses(line.path("senses")) : translations(line);
        if (meaning.isEmpty()) {
            return Optional.empty();
        }
        String pos = cut(line.path("pos").asText("").strip(), POS_MAX);
        return Optional.of(new Row(word, pos.isEmpty() ? null : pos, cut(meaning, TEXT_MAX), vi ? example(line.path("senses")) : null));
    }

    private static String glosses(JsonNode senses) {
        Set<String> out = new LinkedHashSet<>();
        for (JsonNode s : senses) {
            String g = s.path("glosses").path(0).asText("").strip();
            if (!g.isEmpty() && out.size() < GLOSSES_MAX) {
                out.add(g);
            }
        }
        return String.join("; ", out);
    }

    private static String example(JsonNode senses) {
        for (JsonNode s : senses) {
            String t = s.path("examples").path(0).path("text").asText("").strip();
            if (!t.isEmpty()) {
                return cut(t, TEXT_MAX);
            }
        }
        return null;
    }

    /** Bản dịch ở cấp entry hoặc trong senses. */
    private static String translations(JsonNode line) {
        Set<String> out = new LinkedHashSet<>();
        collect(line.path("translations"), out);
        for (JsonNode s : line.path("senses")) {
            collect(s.path("translations"), out);
        }
        return String.join(", ", out);
    }

    private static void collect(JsonNode translations, Set<String> out) {
        for (JsonNode t : translations) {
            String w = t.path("word").asText("").strip();
            if ("vi".equals(t.path("lang_code").asText()) && !w.isEmpty()) {
                out.add(w);
            }
        }
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}

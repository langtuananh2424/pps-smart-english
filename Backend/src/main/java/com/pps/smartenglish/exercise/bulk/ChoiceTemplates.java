package com.pps.smartenglish.exercise.bulk;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.ChoiceInput;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** Bốn dạng có cả dán nhanh lẫn Excel (question-editor-spec 5.2, 5.3): trắc nghiệm, ghép hai vế, điền từ, xếp khối. */
final class ChoiceTemplates {

    private ChoiceTemplates() {}

    private static final String EXPLAIN_NOTE = "Giải thích (tùy chọn): hiện sau khi học viên nộp bài";
    private static final String TAGS_NOTE = "Thẻ (tùy chọn): mã yếu tố ngữ pháp, ngăn bằng dấu phẩy (vd PASSIVE, INVERSION)";

    private static List<String> mcqColumns() {
        List<String> cols = new ArrayList<>(List.of(BaseTemplate.PROMPT));
        "ABCDEFGHIJ".chars().forEach(c -> cols.add("Lựa chọn " + (char) c));
        cols.add("Đáp án đúng");
        return cols;
    }

    private static List<String> numbered(String prefix, int n) {
        return IntStream.rangeClosed(1, n).mapToObj(i -> prefix + i).toList();
    }

    /** Trắc nghiệm một đáp án (CONNECTIVE_MCQ): đáp án đúng ghi nhãn A–J. */
    @Component
    static class Mcq extends BaseTemplate {

        Mcq() {
            super(QuestionType.CONNECTIVE_MCQ, "Trắc nghiệm", mcqColumns(),
                    List.of("Đề: câu hỏi", "Lựa chọn A…J: nội dung từng lựa chọn, để trống ô không dùng (cần ít nhất 2)",
                            "Đáp án đúng: nhãn của đúng một lựa chọn (A–J)", EXPLAIN_NOTE, TAGS_NOTE),
                    List.of(sample("She was tired ___ she kept working.", "B", "because", "although", "so"),
                            sample("He is rich; ___, he is unhappy.", "B", "Therefore", "However")));
        }

        /** Dòng mẫu: đề, các lựa chọn từ A (điền thiếu thì để trống tới J), rồi đáp án đúng. */
        private static List<String> sample(String prompt, String correct, String... options) {
            List<String> row = new ArrayList<>(List.of(prompt));
            for (int i = 0; i < 10; i++) {
                row.add(i < options.length ? options[i] : "");
            }
            row.add(correct);
            return row;
        }

        @Override
        Content content(RowCells row) {
            String prompt = row.need(PROMPT);
            Map<String, String> options = row.suffixed("Lựa chọn");
            String correct = row.need("Đáp án đúng").toUpperCase(Locale.ROOT);
            if (options.keySet().stream().noneMatch(k -> k.toUpperCase(Locale.ROOT).equals(correct))) {
                throw new RowException("Đáp án đúng", "Không khớp lựa chọn nào đã điền");
            }
            List<ChoiceInput> choices = new ArrayList<>();
            options.forEach((k, v) -> choices.add(choice(k.toUpperCase(Locale.ROOT), v, k.toUpperCase(Locale.ROOT).equals(correct))));
            return new Content(prompt, null, null, choices);
        }
    }

    /** Ghép hai vế (CONNECTIVE_MATCH): cột Cặp dạng "1-3, 2-1" nghĩa là vế trái 1 ghép vế phải 3. */
    @Component
    static class Match extends BaseTemplate {

        private static final Pattern PAIR = Pattern.compile("(\\d+)\\s*-\\s*(\\d+)");

        Match() {
            super(QuestionType.CONNECTIVE_MATCH, "Ghép hai vế",
                    Stream.of(List.of(PROMPT), numbered("Vế trái ", 6), numbered("Vế phải ", 6), List.of("Cặp"))
                            .flatMap(List::stream).toList(),
                    List.of("Đề: yêu cầu của câu (vd Nối hai vế)", "Vế trái N / Vế phải N: nội dung từng vế, đánh số từ 1",
                            "Cặp: vế trái - vế phải, ngăn bằng dấu phẩy (vd 1-3, 2-1); mỗi vế trái có đúng một cặp",
                            EXPLAIN_NOTE, TAGS_NOTE),
                    List.of(List.of("Nối hai vế để thành câu đúng", "The road was closed", "He studied hard", "It rained all day",
                            "", "", "", "so we took another route.", "because he wanted a scholarship.",
                            "but the match went ahead.", "", "", "", "1-1, 2-2, 3-3")));
        }

        @Override
        Content content(RowCells row) {
            String prompt = row.need(PROMPT);
            Map<String, String> left = row.suffixed("Vế trái");
            Map<String, String> right = row.suffixed("Vế phải");
            ObjectNode pairs = obj();
            Matcher m = PAIR.matcher(row.need("Cặp"));
            while (m.find()) {
                if (!left.containsKey(m.group(1))) {
                    throw new RowException("Cặp", "Vế trái " + m.group(1) + " chưa điền");
                }
                if (!right.containsKey(m.group(2))) {
                    throw new RowException("Cặp", "Vế phải " + m.group(2) + " chưa điền");
                }
                pairs.put("l" + m.group(1), "r" + m.group(2));
            }
            if (pairs.isEmpty()) {
                throw new RowException("Cặp", "Không đọc được cặp nào, viết dạng 1-3, 2-1");
            }
            ObjectNode payload = obj();
            ArrayNode l = payload.putArray("left");
            left.forEach((k, v) -> l.add(obj().put("id", "l" + k).put("text", v)));
            ArrayNode r = payload.putArray("right");
            // Thứ tự vế phải bị trộn để payload (học viên thấy) không lộ đáp án; ghép đúng nhờ id.
            shuffled(new ArrayList<>(right.entrySet()), prompt).forEach(e -> r.add(obj().put("id", "r" + e.getKey()).put("text", e.getValue())));
            return Content.of(prompt, payload, obj().set("pairs", pairs));
        }
    }

    /** Điền từ (CONNECTIVE_CLOZE): chỗ trống viết {1}, {2}; mỗi chỗ có cột "Đáp án chỗ N", nhiều đáp án ngăn bằng |. */
    @Component
    static class Cloze extends BaseTemplate {

        private static final Pattern BLANK = Pattern.compile("\\{([^{}]+)}");
        private static final String PROMPT_DEFAULT = "Điền từ thích hợp vào mỗi chỗ trống.";

        Cloze() {
            super(QuestionType.CONNECTIVE_CLOZE, "Điền từ",
                    Stream.of(List.of(PROMPT), numbered("Đáp án chỗ ", 6)).flatMap(List::stream).toList(),
                    List.of("Đề: đoạn văn, chỗ trống ghi {1}, {2}…", "Đáp án chỗ N: các đáp án chấp nhận cho chỗ {N}, ngăn bằng |",
                            EXPLAIN_NOTE, TAGS_NOTE),
                    List.of(List.of("He stayed home {1} it rained. {2} he was ill, he went out.", "because | since",
                            "Although | Though", "", "", "", "")));
        }

        @Override
        Content content(RowCells row) {
            String text = row.need(PROMPT);
            Set<String> ids = new LinkedHashSet<>();
            Matcher m = BLANK.matcher(text);
            while (m.find()) {
                ids.add(m.group(1).strip());
            }
            if (ids.isEmpty()) {
                throw new RowException(PROMPT, "Cần ít nhất một chỗ trống dạng {1}");
            }
            Map<String, String> given = row.suffixed("Đáp án chỗ");
            for (String k : given.keySet()) {
                if (ids.stream().noneMatch(id -> id.toLowerCase(Locale.ROOT).equals(k))) {
                    throw new RowException("Đáp án chỗ " + k, "Đề không có chỗ trống {" + k + "}");
                }
            }
            ObjectNode blanks = obj();
            ArrayNode idList = arr();
            for (String id : ids) {
                String cell = row.get("Đáp án chỗ " + id);
                if (cell == null) {
                    throw new RowException("Đáp án chỗ " + id, "Không được để trống");
                }
                ArrayNode accepted = blanks.putArray(id);
                split(cell, "\\|").forEach(accepted::add);
                idList.add(id);
            }
            ObjectNode payload = obj().put("text", text);
            payload.set("blanks", idList);
            return Content.of(PROMPT_DEFAULT, payload, obj().set("blanks", blanks));
        }
    }

    /** Xếp khối (GRAMMAR_BUILD): các khối viết theo thứ tự đúng, ngăn bằng /. */
    @Component
    static class Build extends BaseTemplate {

        Build() {
            super(QuestionType.GRAMMAR_BUILD, "Xếp khối", List.of("Các khối"),
                    List.of("Các khối: các khối theo thứ tự ĐÚNG, ngăn bằng / (dấu / không dùng trong khối)", EXPLAIN_NOTE, TAGS_NOTE),
                    List.of(List.of("She / has / lived / here / since / 2010"), List.of("The new bridge / was built / by local workers")));
        }

        @Override
        Content content(RowCells row) {
            List<String> blocks = split(row.need("Các khối"), "/");
            if (blocks.size() < 2) {
                throw new RowException("Các khối", "Cần ít nhất 2 khối, ngăn bằng /");
            }
            ArrayNode order = arr();
            Map<String, String> byId = new LinkedHashMap<>();
            for (int i = 0; i < blocks.size(); i++) {
                byId.put("b" + (i + 1), blocks.get(i));
                order.add("b" + (i + 1));
            }
            ArrayNode shown = arr();
            // Trộn để học viên không thấy sẵn thứ tự đúng.
            shuffled(new ArrayList<>(byId.entrySet()), String.join("/", blocks))
                    .forEach(e -> shown.add(obj().put("id", e.getKey()).put("text", e.getValue())));
            ObjectNode payload = obj();
            payload.set("blocks", shown);
            return Content.of("Sắp xếp các khối thành câu hoàn chỉnh.", payload, obj().set("order", order));
        }
    }
}

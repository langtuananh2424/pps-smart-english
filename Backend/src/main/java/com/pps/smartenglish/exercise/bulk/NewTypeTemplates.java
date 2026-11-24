package com.pps.smartenglish.exercise.bulk;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.ChoiceInput;
import java.util.ArrayList;
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

/** Năm kiểu chấm mới (FR-EX-04, FR-EX-05; cột chốt ở D-144), chỉ nhập bằng Excel. Cột "Cách chấm" theo D-140. */
final class NewTypeTemplates {

    private NewTypeTemplates() {}

    private static final String EXPLAIN_NOTE = "Giải thích (tùy chọn): hiện sau khi học viên nộp bài";
    private static final String TAGS_NOTE = "Thẻ (tùy chọn): mã yếu tố ngữ pháp, ngăn bằng dấu phẩy";
    private static final String SCORING = "Cách chấm";
    private static final String SCORING_NOTE = "Cách chấm (tùy chọn): Từng phần hoặc Cả câu; để trống thì theo mặc định";

    private static List<String> numbered(String prefix, int n) {
        return IntStream.rangeClosed(1, n).mapToObj(i -> prefix + i).toList();
    }

    private static List<String> lettered(String prefix, int n) {
        return IntStream.range(0, n).mapToObj(i -> prefix + (char) ('A' + i)).toList();
    }

    @SafeVarargs
    private static List<String> cols(List<String>... parts) {
        return Stream.of(parts).flatMap(List::stream).toList();
    }

    /** Dòng mẫu: các nhóm ô, mỗi nhóm được đệm trống tới đúng số cột của nhóm (tránh đếm tay). */
    @SafeVarargs
    private static List<String> row(int[] widths, List<String>... groups) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < widths.length; i++) {
            List<String> g = i < groups.length ? groups[i] : List.of();
            for (int j = 0; j < widths[i]; j++) {
                out.add(j < g.size() ? g.get(j) : "");
            }
        }
        return out;
    }

    /** Trống = mặc định của kiểu; không khớp thì báo đúng cột. */
    private static String scoring(RowCells row) {
        String v = row.get(SCORING);
        if (v == null) {
            return null;
        }
        return switch (RowCells.key(v)) {
            case "từng phần" -> "PARTIAL";
            case "cả câu" -> "ALL";
            default -> throw new RowException(SCORING, "Chỉ nhận \"Từng phần\" hoặc \"Cả câu\"");
        };
    }

    private static ObjectNode withScoring(ObjectNode payload, String scoring) {
        if (scoring != null) {
            payload.put("scoring", scoring);
        }
        return payload;
    }

    /** Chọn nhiều đáp án (CHOICE_MULTI): đáp án đúng ghi nhiều nhãn, vd "A, C". */
    @Component
    static class Multi extends BaseTemplate {

        Multi() {
            super(QuestionType.CHOICE_MULTI, "Chọn nhiều đáp án", cols(List.of(PROMPT), lettered("Lựa chọn ", 10), List.of("Đáp án đúng", SCORING)),
                    List.of("Đề: câu hỏi", "Lựa chọn A…J: nội dung từng lựa chọn (cần ít nhất 2, có cả đúng lẫn sai)",
                            "Đáp án đúng: các nhãn đúng, ngăn bằng dấu phẩy (vd A, C)", SCORING_NOTE, EXPLAIN_NOTE, TAGS_NOTE),
                    List.of(row(new int[] {1, 10, 2}, List.of("Chọn các số chẵn."), List.of("1", "2", "3", "4"), List.of("B, D")),
                            row(new int[] {1, 10, 2}, List.of("Chọn các màu."), List.of("red", "dog", "blue"), List.of("A, C", "Cả câu"))));
        }

        @Override
        Content content(RowCells row) {
            String prompt = row.need(PROMPT);
            Map<String, String> options = row.suffixed("Lựa chọn");
            Set<String> correct = new LinkedHashSet<>();
            split(row.need("Đáp án đúng"), "[,;\\s]+").forEach(s -> correct.add(s.toUpperCase(Locale.ROOT)));
            Set<String> labels = new LinkedHashSet<>();
            options.keySet().forEach(k -> labels.add(k.toUpperCase(Locale.ROOT)));
            if (!labels.containsAll(correct)) {
                throw new RowException("Đáp án đúng", "Có nhãn không khớp lựa chọn nào đã điền");
            }
            List<ChoiceInput> choices = new ArrayList<>();
            options.forEach((k, v) -> choices.add(choice(k.toUpperCase(Locale.ROOT), v, correct.contains(k.toUpperCase(Locale.ROOT)))));
            String scoring = scoring(row);
            return new Content(prompt, scoring == null ? null : withScoring(obj(), scoring), null, choices);
        }
    }

    /** Đúng/Sai (TRUE_FALSE): bộ lựa chọn TF, TFNG hoặc YNNG. */
    @Component
    static class TrueFalse extends BaseTemplate {

        private static final Map<String, List<String>> SETS = Map.of("TF", List.of("TRUE", "FALSE"),
                "TFNG", List.of("TRUE", "FALSE", "NOT_GIVEN"), "YNNG", List.of("YES", "NO", "NOT_GIVEN"));
        private static final Map<String, String> SHORT = Map.of("T", "TRUE", "F", "FALSE", "NG", "NOT_GIVEN", "Y", "YES", "N", "NO");

        TrueFalse() {
            super(QuestionType.TRUE_FALSE, "Đúng Sai", List.of(PROMPT, "Bộ lựa chọn", "Đáp án"),
                    List.of("Đề: nhận định cần xét", "Bộ lựa chọn: TF (Đúng/Sai), TFNG (Đúng/Sai/Không nêu) hoặc YNNG (Có/Không/Không nêu)",
                            "Đáp án: TRUE, FALSE, YES, NO, NOT_GIVEN (hoặc viết tắt T, F, Y, N, NG) thuộc bộ đã chọn", EXPLAIN_NOTE, TAGS_NOTE),
                    List.of(List.of("The sun rises in the west.", "TF", "FALSE"), List.of("The writer likes tea.", "YNNG", "NG")));
        }

        @Override
        Content content(RowCells row) {
            String set = row.need("Bộ lựa chọn").toUpperCase(Locale.ROOT);
            List<String> options = SETS.get(set);
            if (options == null) {
                throw new RowException("Bộ lựa chọn", "Chỉ nhận TF, TFNG hoặc YNNG");
            }
            String answer = row.need("Đáp án").toUpperCase(Locale.ROOT).replace(' ', '_');
            ArrayNode opts = arr();
            options.forEach(opts::add);
            ObjectNode payload = obj();
            payload.set("options", opts);
            return Content.of(row.need(PROMPT), payload, obj().put("answer", SHORT.getOrDefault(answer, answer)));
        }
    }

    /** Điền chữ (FILL_TEXT): nhiều đáp án chấp nhận ngăn bằng |, giới hạn số từ tùy chọn. */
    @Component
    static class FillText extends BaseTemplate {

        FillText() {
            super(QuestionType.FILL_TEXT, "Điền chữ", List.of(PROMPT, "Đáp án chấp nhận", "Số từ tối đa"),
                    List.of("Đề: câu hỏi hoặc câu có chỗ trống", "Đáp án chấp nhận: các đáp án đúng, ngăn bằng |",
                            "Số từ tối đa (tùy chọn): mỗi đáp án không dài quá số từ này", EXPLAIN_NOTE, TAGS_NOTE),
                    List.of(List.of("The capital of France is ___.", "Paris", "1"), List.of("Name one primary colour.", "red | blue | yellow", "")));
        }

        @Override
        Content content(RowCells row) {
            ArrayNode accepted = arr();
            split(row.need("Đáp án chấp nhận"), "\\|").forEach(accepted::add);
            ObjectNode key = obj();
            key.set("accepted", accepted);
            String max = row.get("Số từ tối đa");
            ObjectNode payload = max == null ? null : obj().put("maxWords", positive("Số từ tối đa", max));
            return Content.of(row.need(PROMPT), payload, key);
        }
    }

    /** Hộp từ (WORD_BANK): mỗi chỗ trống {N} có đúng một từ trong hộp. */
    @Component
    static class WordBank extends BaseTemplate {

        private static final Pattern BLANK = Pattern.compile("\\{([^{}]+)}");

        WordBank() {
            super(QuestionType.WORD_BANK, "Hộp từ", cols(List.of(PROMPT, "Hộp từ"), numbered("Đáp án chỗ ", 6), List.of(SCORING)),
                    List.of("Đề: đoạn văn, chỗ trống ghi {1}, {2}…", "Hộp từ: các từ cho học viên chọn, ngăn bằng |",
                            "Đáp án chỗ N: từ trong hộp đúng cho chỗ {N}", SCORING_NOTE, EXPLAIN_NOTE, TAGS_NOTE),
                    List.of(row(new int[] {1, 1, 6, 1}, List.of("The {1} sat on the {2}."), List.of("cat | mat | sky"), List.of("cat", "mat"))));
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
            for (String k : row.suffixed("Đáp án chỗ").keySet()) {
                if (ids.stream().noneMatch(id -> id.toLowerCase(Locale.ROOT).equals(k))) {
                    throw new RowException("Đáp án chỗ " + k, "Đề không có chỗ trống {" + k + "}");
                }
            }
            ObjectNode blanks = obj();
            ArrayNode idList = arr();
            for (String id : ids) {
                blanks.put(id, row.get("Đáp án chỗ " + id) == null
                        ? fail("Đáp án chỗ " + id, "Không được để trống") : row.get("Đáp án chỗ " + id));
                idList.add(id);
            }
            ArrayNode words = arr();
            split(row.need("Hộp từ"), "\\|").forEach(words::add);
            ObjectNode payload = obj().put("text", text);
            payload.set("blanks", idList);
            payload.set("words", words);
            return Content.of("Chọn từ trong hộp để điền vào mỗi chỗ trống.", withScoring(payload, scoring(row)), obj().set("blanks", blanks));
        }

        private static String fail(String field, String message) {
            throw new RowException(field, message);
        }
    }

    /** Chọn mục (MATCH_OPTIONS): mỗi mục chọn một lựa chọn chung A–H; cột Đáp án dạng "1-A, 2-C". */
    @Component
    static class MatchOptions extends BaseTemplate {

        private static final Pattern PAIR = Pattern.compile("(\\d+)\\s*-\\s*([A-Za-z0-9]+)");

        MatchOptions() {
            super(QuestionType.MATCH_OPTIONS, "Chọn mục",
                    cols(List.of(PROMPT), numbered("Mục ", 6), lettered("Lựa chọn ", 8), List.of("Đáp án", SCORING)),
                    List.of("Đề: yêu cầu của câu", "Mục N: các mục cần chọn lựa chọn cho, đánh số từ 1",
                            "Lựa chọn A…H: danh sách lựa chọn chung", "Đáp án: mục-lựa chọn, ngăn bằng dấu phẩy (vd 1-A, 2-C); mỗi mục đúng một đáp án",
                            SCORING_NOTE, EXPLAIN_NOTE, TAGS_NOTE),
                    List.of(row(new int[] {1, 6, 8, 2}, List.of("Chọn tiêu đề cho từng đoạn."), List.of("Paragraph one", "Paragraph two"),
                            List.of("Cats at home", "Dogs at work", "Birds in trees"), List.of("1-A, 2-B"))));
        }

        @Override
        Content content(RowCells row) {
            Map<String, String> items = row.suffixed("Mục");
            Map<String, String> options = row.suffixed("Lựa chọn");
            ObjectNode matches = obj();
            Matcher m = PAIR.matcher(row.need("Đáp án"));
            while (m.find()) {
                String option = m.group(2).toLowerCase(Locale.ROOT);
                if (!items.containsKey(m.group(1))) {
                    throw new RowException("Đáp án", "Mục " + m.group(1) + " chưa điền");
                }
                if (!options.containsKey(option)) {
                    throw new RowException("Đáp án", "Lựa chọn " + m.group(2).toUpperCase(Locale.ROOT) + " chưa điền");
                }
                matches.put("i" + m.group(1), m.group(2).toUpperCase(Locale.ROOT));
            }
            ObjectNode payload = obj();
            ArrayNode i = payload.putArray("items");
            items.forEach((k, v) -> i.add(obj().put("id", "i" + k).put("text", v)));
            ArrayNode o = payload.putArray("options");
            options.forEach((k, v) -> o.add(obj().put("id", k.toUpperCase(Locale.ROOT)).put("text", v)));
            return Content.of(row.need(PROMPT), withScoring(payload, scoring(row)), obj().set("matches", matches));
        }
    }
}

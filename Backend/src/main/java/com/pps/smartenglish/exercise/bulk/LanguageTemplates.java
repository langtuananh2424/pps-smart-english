package com.pps.smartenglish.exercise.bulk;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Các dạng chỉ nhập bằng Excel (question-editor-spec 5.3): nhận diện cấu trúc, tìm và sửa lỗi, hai dạng viết lại, viết, nói.
 * Vị trí từ (Cụm, Vị trí từ sai) đếm từ 1 theo từ tách bởi khoảng trắng; dấu câu dính từ liền trước thì tính chung một từ.
 */
final class LanguageTemplates {

    private LanguageTemplates() {}

    private static final String EXPLAIN_NOTE = "Giải thích (tùy chọn): hiện sau khi học viên nộp bài";
    private static final String TAGS_NOTE = "Thẻ (tùy chọn): mã yếu tố ngữ pháp, ngăn bằng dấu phẩy";
    private static final String SAMPLE_ANSWERS_NOTE = "Đáp án mẫu (tùy chọn): các câu trả lời chấp nhận, ngăn bằng |";

    private static List<String> tokens(String sentence) {
        return BaseTemplate.split(sentence, "\\s+");
    }

    private static ArrayNode strings(List<String> items) {
        ArrayNode a = BaseTemplate.arr();
        items.forEach(a::add);
        return a;
    }

    /** Nhận diện cấu trúc (GRAMMAR_IDENTIFY): cột Cụm dạng "3-6:PASSIVE; 8-9:INVERSION" (từ đầu-từ cuối:mã yếu tố). */
    @Component
    static class Identify extends BaseTemplate {

        private static final Pattern SPAN = Pattern.compile("(\\d+)(?:\\s*-\\s*(\\d+))?\\s*:\\s*([A-Za-z_]+)");

        Identify() {
            super(QuestionType.GRAMMAR_IDENTIFY, "Nhận diện cấu trúc", List.of("Câu", "Cụm"),
                    List.of("Câu: câu cần nhận diện, các từ cách nhau bằng khoảng trắng",
                            "Cụm: từ-đầu-từ-cuối:MÃ, ngăn bằng ; (vd 3-6:PASSIVE; 8-9:INVERSION), đếm từ 1", EXPLAIN_NOTE, TAGS_NOTE),
                    List.of(List.of("The book that I bought yesterday was expensive .", "3-6:RELATIVE_CLAUSE")));
        }

        @Override
        Content content(RowCells row) {
            List<String> tokens = tokens(row.need("Câu"));
            ArrayNode spans = arr();
            for (String part : split(row.need("Cụm"), ";")) {
                Matcher m = SPAN.matcher(part);
                if (!m.matches()) {
                    throw new RowException("Cụm", "Không đọc được \"" + part + "\", viết dạng 3-6:PASSIVE");
                }
                int start = Integer.parseInt(m.group(1));
                int end = m.group(2) == null ? start : Integer.parseInt(m.group(2));
                if (start < 1 || end < start || end > tokens.size()) {
                    throw new RowException("Cụm", "Vị trí phải trong 1.." + tokens.size() + " và từ đầu không sau từ cuối");
                }
                spans.add(obj().put("start", start - 1).put("end", end - 1).put("element", m.group(3).toUpperCase(Locale.ROOT)));
            }
            ObjectNode payload = obj();
            payload.set("tokens", strings(tokens));
            return Content.of("Chọn cụm từ thể hiện cấu trúc ngữ pháp trong câu.", payload, obj().set("spans", spans));
        }
    }

    /** Tìm và sửa lỗi (GRAMMAR_SPOT_FIX): một từ sai (vị trí từ 1) và các cách sửa chấp nhận. */
    @Component
    static class SpotFix extends BaseTemplate {

        SpotFix() {
            super(QuestionType.GRAMMAR_SPOT_FIX, "Tìm và sửa lỗi", List.of("Câu", "Vị trí từ sai", "Cách sửa"),
                    List.of("Câu: câu có một lỗi, các từ cách nhau bằng khoảng trắng", "Vị trí từ sai: thứ tự từ sai, đếm từ 1",
                            "Cách sửa: các từ sửa đúng chấp nhận, ngăn bằng |", EXPLAIN_NOTE, TAGS_NOTE),
                    List.of(List.of("The letter was write by Tom .", "4", "written")));
        }

        @Override
        Content content(RowCells row) {
            List<String> tokens = tokens(row.need("Câu"));
            int position = positive("Vị trí từ sai", row.need("Vị trí từ sai"));
            if (position > tokens.size()) {
                throw new RowException("Vị trí từ sai", "Câu chỉ có " + tokens.size() + " từ");
            }
            ObjectNode payload = obj();
            payload.set("tokens", strings(tokens));
            ObjectNode key = obj().put("position", position - 1);
            key.set("corrections", strings(split(row.need("Cách sửa"), "\\|")));
            return Content.of("Tìm lỗi sai trong câu và sửa lại.", payload, key);
        }
    }

    /** Khóa đáp án mẫu của hai dạng viết lại; không có đáp án mẫu thì để AI chấm. */
    private static ObjectNode accepted(RowCells row) {
        String cell = row.get("Đáp án mẫu");
        if (cell == null) {
            return null;
        }
        ObjectNode key = BaseTemplate.obj();
        key.set("acceptedAnswers", strings(BaseTemplate.split(cell, "\\|")));
        return key;
    }

    /** Viết lại dùng từ nối (CONNECTIVE_REWRITE). */
    @Component
    static class ConnectiveRewrite extends BaseTemplate {

        ConnectiveRewrite() {
            super(QuestionType.CONNECTIVE_REWRITE, "Viết lại (từ nối)", List.of("Câu gốc", "Từ nối", "Đáp án mẫu"),
                    List.of("Câu gốc: câu hoặc các câu cần viết lại", "Từ nối: từ nối bắt buộc có trong câu trả lời",
                            SAMPLE_ANSWERS_NOTE, EXPLAIN_NOTE, TAGS_NOTE),
                    List.of(List.of("The test was difficult. Most students passed it.", "although",
                            "Although the test was difficult, most students passed it. | Most students passed the test although it was difficult.")));
        }

        @Override
        Content content(RowCells row) {
            String word = row.need("Từ nối");
            ObjectNode payload = obj().put("sentence", row.need("Câu gốc")).put("requiredWord", word);
            return Content.of("Viết lại câu, dùng từ nối \"" + word + "\".", payload, accepted(row));
        }
    }

    /** Viết lại theo cấu trúc (GRAMMAR_REWRITE): Yếu tố là mã yếu tố ngữ pháp bắt buộc dùng. */
    @Component
    static class GrammarRewrite extends BaseTemplate {

        GrammarRewrite() {
            super(QuestionType.GRAMMAR_REWRITE, "Viết lại (cấu trúc)", List.of("Câu gốc", "Yếu tố", "Đáp án mẫu"),
                    List.of("Câu gốc: câu cần viết lại", "Yếu tố: mã yếu tố ngữ pháp bắt buộc dùng (vd PASSIVE)",
                            SAMPLE_ANSWERS_NOTE, EXPLAIN_NOTE, TAGS_NOTE),
                    List.of(List.of("People say that regular exercise improves memory.", "PASSIVE",
                            "Regular exercise is said to improve memory. | It is said that regular exercise improves memory.")));
        }

        @Override
        Content content(RowCells row) {
            ObjectNode payload = obj().put("sentence", row.need("Câu gốc"))
                    .put("requiredElement", row.need("Yếu tố").toUpperCase(Locale.ROOT));
            return Content.of("Viết lại câu theo cấu trúc yêu cầu.", payload, accepted(row));
        }
    }

    /** Viết phát triển ý (WRITING). */
    @Component
    static class Writing extends BaseTemplate {

        Writing() {
            super(QuestionType.WRITING, "Viết", List.of(PROMPT, "Số từ"),
                    List.of("Đề: đề bài viết", "Số từ: số từ yêu cầu tối thiểu", EXPLAIN_NOTE, TAGS_NOTE),
                    List.of(List.of("Describe your favourite place in your city.", "120")));
        }

        @Override
        Content content(RowCells row) {
            String task = row.need(PROMPT);
            return Content.of(task, obj().put("task", task).put("requiredWords", positive("Số từ", row.need("Số từ"))), null);
        }
    }

    /** Nói (SPEAKING): Part 1–3, thời gian tối đa tùy chọn (giây). */
    @Component
    static class Speaking extends BaseTemplate {

        Speaking() {
            super(QuestionType.SPEAKING, "Nói", List.of(PROMPT, "Part", "Thời gian (giây)"),
                    List.of("Đề: câu hỏi cho học viên", "Part: 1, 2 hoặc 3", "Thời gian (giây): tùy chọn, 10–180; trống thì theo mặc định của Part",
                            EXPLAIN_NOTE, TAGS_NOTE),
                    List.of(List.of("What do you usually do on weekends?", "1", "")));
        }

        @Override
        Content content(RowCells row) {
            String prompt = row.need(PROMPT);
            ObjectNode payload = obj().put("part", positive("Part", row.need("Part"))).put("prompt", prompt);
            String seconds = row.get("Thời gian (giây)");
            if (seconds != null) {
                payload.put("maxSeconds", positive("Thời gian (giây)", seconds));
            }
            return Content.of(prompt, payload, null);
        }
    }
}

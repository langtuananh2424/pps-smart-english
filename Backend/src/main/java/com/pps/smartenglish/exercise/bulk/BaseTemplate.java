package com.pps.smartenglish.exercise.bulk;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.ChoiceInput;
import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.CreateQuestion;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Pattern;

/**
 * Khung chung (Template Method): cột "Giải thích" và "Thẻ" có ở mọi dạng; lớp con chỉ dựng nội dung riêng của dạng.
 * Ô có thẻ HTML đi đường promptHtml/contentHtml để bị làm sạch như khi soạn tay; ô chữ thường giữ nguyên.
 */
abstract class BaseTemplate implements ImportTemplate {

    static final String EXPLAIN = "Giải thích";
    static final String TAGS = "Thẻ";
    static final String PROMPT = "Đề";

    private static final Pattern HTML_TAG = Pattern.compile("<[A-Za-z/!?]");
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    /** Nội dung riêng của dạng; prompt là câu hiển thị (có thể là lời hướng dẫn mặc định). */
    record Content(String prompt, ObjectNode payload, ObjectNode answerKey, List<ChoiceInput> choices) {
        static Content of(String prompt, ObjectNode payload, ObjectNode key) {
            return new Content(prompt, payload, key, null);
        }
    }

    private final QuestionType type;
    private final String label;
    private final List<String> columns;
    private final List<String> notes;
    private final List<List<String>> samples;

    BaseTemplate(QuestionType type, String label, List<String> columns, List<String> notes, List<List<String>> samples) {
        this.type = type;
        this.label = label;
        this.columns = columns;
        this.notes = notes;
        this.samples = samples;
    }

    abstract Content content(RowCells row);

    @Override
    public final QuestionType type() {
        return type;
    }

    @Override
    public final String label() {
        return label;
    }

    @Override
    public final List<String> columns() {
        List<String> all = new ArrayList<>(columns);
        all.add(EXPLAIN);
        all.add(TAGS);
        return all;
    }

    @Override
    public final List<String> columnNotes() {
        return notes;
    }

    /** Dòng mẫu chỉ ghi các cột riêng của dạng; hai cột chung để trống cho người dùng tự điền. */
    @Override
    public final List<List<String>> samples() {
        return samples.stream().map(r -> {
            List<String> full = new ArrayList<>(r);
            while (full.size() < columns.size() + 2) {
                full.add("");
            }
            return full;
        }).toList();
    }

    @Override
    public final CreateQuestion toRequest(RowCells row) {
        Content c = content(row);
        boolean html = HTML_TAG.matcher(c.prompt()).find();
        return new CreateQuestion(type.name(), html ? null : c.prompt(), html ? c.prompt() : null, null, c.payload(), c.answerKey(),
                c.choices(), row.get(EXPLAIN), null, tags(row.get(TAGS)), null, null, null);
    }

    // ---- tiện ích cho lớp con ----

    static ObjectNode obj() {
        return JSON.objectNode();
    }

    static ArrayNode arr() {
        return JSON.arrayNode();
    }

    static ChoiceInput choice(String label, String text, boolean correct) {
        boolean html = HTML_TAG.matcher(text).find();
        return new ChoiceInput(label, html ? null : text, html ? text : null, correct, null);
    }

    /** Tách theo dấu phân cách, bỏ phần rỗng. */
    static List<String> split(String s, String regex) {
        return Arrays.stream(s.split(regex)).map(String::strip).filter(x -> !x.isEmpty()).toList();
    }

    static List<String> tags(String cell) {
        return cell == null ? List.of() : split(cell, "[,;]").stream().map(t -> t.toUpperCase(Locale.ROOT)).toList();
    }

    /** Số nguyên dương trong ô; lỗi thì báo đúng cột. */
    static int positive(String column, String value) {
        try {
            int n = Integer.parseInt(value.strip());
            if (n >= 1) {
                return n;
            }
        } catch (NumberFormatException ignored) {
            // báo lỗi bên dưới
        }
        throw new RowException(column, "Phải là số nguyên từ 1 trở lên");
    }

    /**
     * Trộn có hạt giống cố định (cùng nội dung ra cùng thứ tự) để payload không lộ đáp án qua thứ tự nhập; luôn khác
     * thứ tự gốc khi có từ 2 phần tử.
     */
    static <T> List<T> shuffled(List<T> items, String seed) {
        List<T> out = new ArrayList<>(items);
        Collections.shuffle(out, new Random(seed.hashCode()));
        if (out.size() > 1 && out.equals(items)) {
            Collections.rotate(out, 1);
        }
        return out;
    }
}

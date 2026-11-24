package com.pps.smartenglish.exercise.bulk;

import com.pps.smartenglish.exercise.bulk.ImportSource.SourceRow;
import com.pps.smartenglish.exercise.domain.QuestionType;
import com.pps.smartenglish.shared.error.ApiException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/**
 * Dán nhanh (question-editor-spec 5.2): "# Dạng" mở một mục, "Q:" mở một câu. Mỗi khối được đổi sang đúng các cột của
 * Excel rồi đi qua cùng template, nên dán nhanh và Excel luôn cho cùng một câu.
 */
final class PasteSource implements ImportSource {

    private static final Pattern QUESTION = Pattern.compile("^(?i:q)\\s*:\\s*(.*)$");
    private static final Pattern OPTION = Pattern.compile("^(\\*?)\\s*([A-Za-z0-9]{1,10})\\)\\s*(.+)$");
    private static final Pattern BLANK_ANSWER = Pattern.compile("^([^\\s:]{1,20})\\s*:\\s*(.+)$");
    private static final Pattern EXPLAIN = Pattern.compile("^(?iu:giải thích)\\s*:\\s*(.*)$");
    private static final Pattern TAGS = Pattern.compile("^(?iu:thẻ)\\s*:\\s*(.*)$");
    private static final String SUPPORTED = "Trắc nghiệm, Ghép hai vế, Điền từ, Xếp khối";

    private record Line(int no, String text) {}

    private static final class Block {
        final ImportTemplate template;
        final int line;
        final String question;
        final List<Line> lines = new ArrayList<>();

        Block(ImportTemplate template, int line, String question) {
            this.template = template;
            this.line = line;
            this.question = question;
        }
    }

    private final String text;
    private final TemplateRegistry templates;
    private final ImportProperties props;

    PasteSource(String text, TemplateRegistry templates, ImportProperties props) {
        this.text = text;
        this.templates = templates;
        this.props = props;
    }

    @Override
    public String name() {
        return "PASTE";
    }

    @Override
    public Parsed read() {
        if (text.length() > props.maxPasteChars()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "import_too_large", "Nội dung dán quá dài (tối đa " + props.maxPasteChars() + " ký tự)");
        }
        List<SourceRow> rows = new ArrayList<>();
        ImportTemplate section = null;
        boolean ignoring = false; // sau một tiêu đề sai: bỏ qua tới tiêu đề kế để không báo lỗi dây chuyền
        Block cur = null;
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].strip();
            int no = i + 1;
            if (t.isEmpty()) {
                continue;
            }
            if (t.startsWith("#")) {
                flush(cur, rows);
                cur = null;
                section = templates.byName(t.substring(1)).filter(x -> TemplateRegistry.pasteSupported(x.type())).orElse(null);
                ignoring = section == null;
                if (ignoring) {
                    rows.add(SourceRow.bad("", no, "Dạng \"" + t.substring(1).strip() + "\" chưa hỗ trợ dán nhanh (dùng: " + SUPPORTED + "), hãy nhập bằng Excel"));
                }
                continue;
            }
            if (ignoring) {
                continue;
            }
            Matcher q = QUESTION.matcher(t);
            if (q.matches()) {
                flush(cur, rows);
                cur = section == null ? null : new Block(section, no, q.group(1).strip());
                if (section == null) {
                    rows.add(SourceRow.bad("", no, "Thiếu dòng tiêu đề dạng phía trên, vd \"# Trắc nghiệm\""));
                    ignoring = true;
                }
            } else if (cur == null) {
                rows.add(SourceRow.bad("", no, "Dòng nằm ngoài câu hỏi: mỗi câu bắt đầu bằng \"Q:\""));
                ignoring = true;
            } else {
                cur.lines.add(new Line(no, t));
            }
            if (rows.size() > props.maxRows()) {
                throw tooMany();
            }
        }
        flush(cur, rows);
        if (rows.size() > props.maxRows()) {
            throw tooMany();
        }
        return new Parsed(rows, List.of());
    }

    private ApiException tooMany() {
        return new ApiException(HttpStatus.BAD_REQUEST, "import_too_many_rows", "Tối đa " + props.maxRows() + " câu mỗi lần nhập");
    }

    private static void flush(Block b, List<SourceRow> rows) {
        if (b != null) {
            rows.add(toRow(b));
        }
    }

    private static SourceRow toRow(Block b) {
        String sheet = b.template.label();
        Map<String, String> cells = new LinkedHashMap<>();
        QuestionType type = b.template.type();
        cells.put(type == QuestionType.GRAMMAR_BUILD ? "Các khối" : BaseTemplate.PROMPT, b.question);
        List<String> pairs = new ArrayList<>();
        String correct = null;
        for (Line l : b.lines) {
            Matcher m;
            if ((m = EXPLAIN.matcher(l.text)).matches()) {
                cells.put(BaseTemplate.EXPLAIN, m.group(1));
            } else if ((m = TAGS.matcher(l.text)).matches()) {
                cells.put(BaseTemplate.TAGS, m.group(1));
            } else if (type == QuestionType.CONNECTIVE_MCQ && (m = OPTION.matcher(l.text)).matches()) {
                cells.put("Lựa chọn " + m.group(2), m.group(3));
                if (!m.group(1).isEmpty()) {
                    if (correct != null) {
                        return SourceRow.bad(sheet, b.line, "Dòng " + l.no + ": chỉ được đánh dấu * cho một đáp án");
                    }
                    correct = m.group(2);
                }
            } else if (type == QuestionType.CONNECTIVE_CLOZE && (m = BLANK_ANSWER.matcher(l.text)).matches()) {
                cells.put("Đáp án chỗ " + m.group(1), m.group(2));
            } else if (type == QuestionType.CONNECTIVE_MATCH && l.text.contains("->")) {
                String[] sides = l.text.split("->", 2);
                if (sides[0].isBlank() || sides[1].isBlank()) {
                    return SourceRow.bad(sheet, b.line, "Dòng " + l.no + ": cần dạng \"vế trái -> vế phải\"");
                }
                int n = pairs.size() + 1;
                cells.put("Vế trái " + n, sides[0].strip());
                cells.put("Vế phải " + n, sides[1].strip());
                pairs.add(n + "-" + n);
            } else {
                return SourceRow.bad(sheet, b.line, "Dòng " + l.no + ": không nhận ra \"" + abbreviate(l.text) + "\"");
            }
        }
        if (correct != null) {
            cells.put("Đáp án đúng", correct);
        }
        if (!pairs.isEmpty()) {
            cells.put("Cặp", String.join(", ", pairs));
        }
        return new SourceRow(sheet, b.line, b.template, cells, null);
    }

    private static String abbreviate(String s) {
        return s.length() > 40 ? s.substring(0, 40) + "…" : s;
    }
}

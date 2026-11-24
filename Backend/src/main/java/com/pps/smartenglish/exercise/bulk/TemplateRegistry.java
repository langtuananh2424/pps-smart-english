package com.pps.smartenglish.exercise.bulk;

import com.pps.smartenglish.exercise.domain.QuestionType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Chọn template theo tên sheet/tiêu đề hoặc theo dạng (cùng kiểu GraderRegistry); thứ tự theo bean để file mẫu ổn định. */
@Component
public class TemplateRegistry {

    private final List<ImportTemplate> all;
    private final Map<String, ImportTemplate> byLabel = new LinkedHashMap<>();

    TemplateRegistry(List<ImportTemplate> all) {
        this.all = all.stream().sorted(java.util.Comparator.comparing(t -> t.type().ordinal())).toList();
        this.all.forEach(t -> byLabel.put(RowCells.key(t.label()), t));
    }

    public List<ImportTemplate> all() {
        return all;
    }

    /** Khớp tên sheet theo nhãn tiếng Việt hoặc theo tên dạng (CONNECTIVE_MCQ...). */
    public Optional<ImportTemplate> byName(String name) {
        String k = RowCells.key(name);
        if (k.equals("ghép")) { // tiêu đề rút gọn trong ví dụ của spec 5.2
            k = RowCells.key("Ghép hai vế");
        }
        String key = k;
        return Optional.ofNullable(byLabel.get(k)).or(() -> all.stream().filter(t -> RowCells.key(t.type().name()).equals(key)).findFirst());
    }

    public Optional<ImportTemplate> byType(String type) {
        return all.stream().filter(t -> t.type().name().equals(type)).findFirst();
    }

    /** Bốn dạng dán nhanh hỗ trợ (spec 5.2). */
    public static boolean pasteSupported(QuestionType type) {
        return type == QuestionType.CONNECTIVE_MCQ || type == QuestionType.CONNECTIVE_MATCH
                || type == QuestionType.CONNECTIVE_CLOZE || type == QuestionType.GRAMMAR_BUILD;
    }
}

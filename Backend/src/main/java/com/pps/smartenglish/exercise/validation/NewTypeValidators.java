package com.pps.smartenglish.exercise.validation;

import static com.pps.smartenglish.exercise.domain.QuestionType.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * D-154, D-157: mỗi kiểu mới một validator, khớp đúng định dạng grader đọc. Chỗ trống (slot) và id lựa chọn chỉ được kiểm chéo với
 * layout của nhóm ở GroupLayoutCheck, vì layout không có trong QuestionDraft.
 */
final class NewTypeValidators {

    private static final Set<String> NONE = Set.of();
    private static final Set<String> SLOT = Set.of("slot");

    private NewTypeValidators() {}

    // ---- gõ chữ: key {accepted} ----

    /** payload tùy chọn {maxWords}; audio ở nhóm (kiểm ở GroupLayoutCheck). Chép chính tả bỏ dấu câu khi so. */
    @Component
    static class Dictation extends TypedTextValidator { Dictation() { super(DICTATION, NONE, NONE, true); } }

    @Component
    static class ListenForm extends TypedTextValidator { ListenForm() { super(LISTEN_FORM, SLOT, NONE, false); } }

    /** payload {before?, after?, maxWords?}, ít nhất một trong before/after. */
    @Component
    static class Sentence extends TypedTextValidator {
        Sentence() { super(IELTS_SENTENCE, NONE, Set.of("before", "after"), false); }

        @Override
        void extra(JsonNode payload) {
            if (payload == null || !(payload.has("before") || payload.has("after"))) {
                throw JsonRules.bad("payload", "Cần before hoặc after");
            }
        }
    }

    @Component
    static class Summary extends TypedTextValidator { Summary() { super(IELTS_SUMMARY, SLOT, NONE, false); } }

    @Component
    static class Note extends TypedTextValidator { Note() { super(IELTS_NOTE, SLOT, NONE, false); } }

    @Component
    static class Table extends TypedTextValidator { Table() { super(IELTS_TABLE, SLOT, NONE, false); } }

    @Component
    static class Flowchart extends TypedTextValidator { Flowchart() { super(IELTS_FLOWCHART, SLOT, NONE, false); } }

    @Component
    static class Form extends TypedTextValidator { Form() { super(IELTS_FORM, SLOT, NONE, false); } }

    @Component
    static class Diagram extends TypedTextValidator { Diagram() { super(IELTS_DIAGRAM, Set.of("slot", "description"), NONE, false); } }

    @Component
    static class Short extends TypedTextValidator { Short() { super(IELTS_SHORT, Set.of("question"), NONE, false); } }

    // ---- chọn một giá trị ----

    @Component
    static class Tfng extends PickValidator {
        Tfng() { super(IELTS_TFNG, Set.of("statement"), "value", Set.of("TRUE", "FALSE", "NOT_GIVEN")); }
    }

    @Component
    static class Ynng extends PickValidator {
        Ynng() { super(IELTS_YNNG, Set.of("statement"), "value", Set.of("YES", "NO", "NOT_GIVEN")); }
    }

    @Component
    static class MatchInfo extends PickValidator { MatchInfo() { super(IELTS_MATCH_INFO, Set.of("statement"), "option", null); } }

    @Component
    static class MatchHeadings extends PickValidator { MatchHeadings() { super(IELTS_MATCH_HEADINGS, Set.of("paragraph"), "option", null); } }

    @Component
    static class MatchFeatures extends PickValidator { MatchFeatures() { super(IELTS_MATCH_FEATURES, Set.of("statement"), "option", null); } }

    @Component
    static class MatchEndings extends PickValidator { MatchEndings() { super(IELTS_MATCH_ENDINGS, Set.of("stem"), "option", null); } }

    @Component
    static class Matching extends PickValidator { Matching() { super(IELTS_MATCHING, Set.of("statement"), "option", null); } }

    @Component
    static class SummaryBank extends PickValidator { SummaryBank() { super(IELTS_SUMMARY_BANK, SLOT, "word", null); } }

    @Component
    static class MapLabel extends PickValidator { MapLabel() { super(IELTS_MAP, Set.of("label"), "option", null); } }
}

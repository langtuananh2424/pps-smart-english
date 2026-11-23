package com.pps.smartenglish.exercise.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pps.smartenglish.exercise.validation.QuestionDraft.ChoiceDraft;
import com.pps.smartenglish.shared.error.ApiException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** FR-EX-04/05: payload/answerKey thiếu hoặc sai bị từ chối khi tạo câu. */
class NewValidatorsTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode j(String s) {
        try {
            return s == null ? null : JSON.readTree(s);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static QuestionDraft d(String payload, String key) {
        return new QuestionDraft(j(payload), j(key), List.of());
    }

    private static void ok(QuestionValidator v, QuestionDraft d) {
        assertDoesNotThrow(() -> v.validate(d, Set.of()));
    }

    private static void bad(QuestionValidator v, QuestionDraft d) {
        assertThrows(ApiException.class, () -> v.validate(d, Set.of()));
    }

    private static ChoiceDraft c(String label, boolean correct) {
        return new ChoiceDraft(label, "text " + label, null, correct, null);
    }

    // ---- CHOICE_MULTI ----

    private final MultiChoiceValidator multi = new MultiChoiceValidator();

    @Test
    void multiChoice() {
        ok(multi, new QuestionDraft(null, null, List.of(c("A", true), c("B", true), c("C", false))));
        ok(multi, new QuestionDraft(j("{\"scoring\":\"ALL\"}"), null, List.of(c("A", true), c("B", false))));
        bad(multi, new QuestionDraft(null, null, List.of(c("A", true)))); // dưới 2 lựa chọn
        bad(multi, new QuestionDraft(null, null, List.of(c("A", false), c("B", false)))); // không có đáp án đúng
        bad(multi, new QuestionDraft(null, null, List.of(c("A", true), c("B", true)))); // không có lựa chọn sai
        bad(multi, new QuestionDraft(null, null, List.of(c("A", true), c("A", false)))); // nhãn lặp
        bad(multi, new QuestionDraft(j("{\"scoring\":\"HALF\"}"), null, List.of(c("A", true), c("B", false))));
        bad(multi, new QuestionDraft(j("{\"x\":1}"), null, List.of(c("A", true), c("B", false))));
        bad(multi, new QuestionDraft(null, j("{\"a\":1}"), List.of(c("A", true), c("B", false)))); // không có answerKey
    }

    // ---- TRUE_FALSE ----

    private final TrueFalseValidator tf = new TrueFalseValidator();

    @Test
    void trueFalse() {
        ok(tf, d("{\"options\":[\"TRUE\",\"FALSE\"]}", "{\"answer\":\"FALSE\"}"));
        ok(tf, d("{\"options\":[\"TRUE\",\"FALSE\",\"NOT_GIVEN\"]}", "{\"answer\":\"NOT_GIVEN\"}"));
        ok(tf, d("{\"options\":[\"YES\",\"NO\",\"NOT_GIVEN\"]}", "{\"answer\":\"YES\"}"));
        bad(tf, d(null, "{\"answer\":\"TRUE\"}"));
        bad(tf, d("{\"options\":[\"TRUE\",\"NO\"]}", "{\"answer\":\"TRUE\"}")); // tập lạ
        bad(tf, d("{\"options\":[\"FALSE\",\"TRUE\"]}", "{\"answer\":\"TRUE\"}")); // sai thứ tự hiển thị
        bad(tf, d("{\"options\":[\"TRUE\",\"FALSE\"]}", "{\"answer\":\"NOT_GIVEN\"}")); // ngoài tập
        bad(tf, d("{\"options\":[\"TRUE\",\"FALSE\"]}", null));
        bad(tf, d("{\"options\":[\"TRUE\",\"FALSE\"],\"answer\":\"TRUE\"}", "{\"answer\":\"TRUE\"}")); // đáp án lọt vào payload
    }

    // ---- FILL_TEXT ----

    private final FillTextValidator fill = new FillTextValidator();

    @Test
    void fillText() {
        ok(fill, d(null, "{\"accepted\":[\"ice cream\",\"gelato\"]}"));
        ok(fill, d("{\"maxWords\":2}", "{\"accepted\":[\"ice cream\"]}"));
        bad(fill, d(null, null));
        bad(fill, d(null, "{\"accepted\":[]}"));
        bad(fill, d(null, "{\"accepted\":[\" \"]}"));
        bad(fill, d(null, "{\"accepted\":\"x\"}"));
        bad(fill, d("{\"maxWords\":0}", "{\"accepted\":[\"x\"]}"));
        bad(fill, d("{\"maxWords\":\"2\"}", "{\"accepted\":[\"x\"]}"));
        bad(fill, d("{\"maxWords\":1}", "{\"accepted\":[\"ice cream\"]}")); // đáp án vượt giới hạn
        bad(fill, d("{\"limit\":1}", "{\"accepted\":[\"x\"]}"));
    }

    // ---- WORD_BANK ----

    private final WordBankValidator bank = new WordBankValidator();
    private static final String PAYLOAD = "{\"text\":\"The {1} chased the {2}.\",\"blanks\":[\"1\",\"2\"],\"words\":[\"cat\",\"mouse\",\"dog\"]}";

    @Test
    void wordBank() {
        ok(bank, d(PAYLOAD, "{\"blanks\":{\"1\":\"dog\",\"2\":\"Cat\"}}"));
        bad(bank, d(PAYLOAD, "{\"blanks\":{\"1\":\"dog\"}}")); // thiếu chỗ trống
        bad(bank, d(PAYLOAD, "{\"blanks\":{\"1\":\"dog\",\"2\":\"fish\"}}")); // từ không có trong hộp
        bad(bank, d(PAYLOAD, "{\"blanks\":{\"1\":\"dog\",\"2\":\"dog\"}}")); // dùng lại khi không cho phép
        bad(bank, d(PAYLOAD.replace("{2}", "x"), "{\"blanks\":{\"1\":\"dog\",\"2\":\"cat\"}}")); // text lệch blanks
        bad(bank, d(PAYLOAD.replace("\"dog\"]", "\"Cat\"]"), "{\"blanks\":{\"1\":\"cat\",\"2\":\"mouse\"}}")); // từ lặp trong hộp
        bad(bank, d(PAYLOAD.substring(0, PAYLOAD.length() - 1) + ",\"allowReuse\":\"yes\"}","{\"blanks\":{\"1\":\"dog\",\"2\":\"cat\"}}"));
        bad(bank, d(null, "{\"blanks\":{\"1\":\"dog\"}}"));
        ok(bank, d(PAYLOAD.substring(0, PAYLOAD.length() - 1) + ",\"allowReuse\":true}", "{\"blanks\":{\"1\":\"dog\",\"2\":\"dog\"}}"));
    }

    // ---- MATCH_OPTIONS ----

    private final MatchOptionsValidator match = new MatchOptionsValidator();
    private static final String MATCH_PAYLOAD = "{\"items\":[{\"id\":\"q1\",\"text\":\"x\"},{\"id\":\"q2\",\"text\":\"y\"}],"
            + "\"options\":[{\"id\":\"A\",\"text\":\"one\"},{\"id\":\"B\",\"text\":\"two\"}]}";

    @Test
    void matchOptions() {
        ok(match, d(MATCH_PAYLOAD, "{\"matches\":{\"q1\":\"A\",\"q2\":\"A\"}}")); // lặp lựa chọn hợp lệ
        bad(match, d(MATCH_PAYLOAD, "{\"matches\":{\"q1\":\"A\"}}"));
        bad(match, d(MATCH_PAYLOAD, "{\"matches\":{\"q1\":\"A\",\"q2\":\"Z\"}}"));
        bad(match, d(MATCH_PAYLOAD, "{\"matches\":{\"q1\":\"A\",\"q2\":\"A\",\"q3\":\"B\"}}"));
        bad(match, d(MATCH_PAYLOAD, null));
        bad(match, d(null, "{\"matches\":{\"q1\":\"A\"}}"));
        bad(match, d(MATCH_PAYLOAD.replace("\"id\":\"B\"", "\"id\":\"A\""), "{\"matches\":{\"q1\":\"A\",\"q2\":\"A\"}}")); // id lặp
        bad(match, d(MATCH_PAYLOAD.replace(",{\"id\":\"B\",\"text\":\"two\"}", ""), "{\"matches\":{\"q1\":\"A\",\"q2\":\"A\"}}")); // dưới 2 lựa chọn
    }

    @Test
    void scoringModeAcceptedOnlyAsPartialOrAll() {
        String wk = "{\"blanks\":{\"1\":\"dog\",\"2\":\"cat\"}}";
        for (String m : new String[] {"PARTIAL", "ALL"}) {
            ok(bank, d(PAYLOAD.substring(0, PAYLOAD.length() - 1) + ",\"scoring\":\"" + m + "\"}", wk));
            ok(match, d(MATCH_PAYLOAD.substring(0, MATCH_PAYLOAD.length() - 1) + ",\"scoring\":\"" + m + "\"}", "{\"matches\":{\"q1\":\"A\",\"q2\":\"A\"}}"));
        }
        bad(bank, d(PAYLOAD.substring(0, PAYLOAD.length() - 1) + ",\"scoring\":\"HALF\"}", wk));
        bad(match, d(MATCH_PAYLOAD.substring(0, MATCH_PAYLOAD.length() - 1) + ",\"scoring\":\"HALF\"}", "{\"matches\":{\"q1\":\"A\",\"q2\":\"A\"}}"));
    }
}

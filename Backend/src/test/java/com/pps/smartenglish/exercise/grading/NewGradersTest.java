package com.pps.smartenglish.exercise.grading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** FR-EX-04/05: 5 kiểu chấm mới, thuần trong bộ nhớ. */
class NewGradersTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode j(String s) {
        try {
            return JSON.readTree(s);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static GradingInput in(String payload, String key) {
        return new GradingInput(key == null ? null : j(key), Set.of(), payload == null ? null : j(payload));
    }

    private static void assertCredit(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    // ---- CHOICE_MULTI ----

    private final MultiChoiceGrader multi = new MultiChoiceGrader();

    private static GradingInput choices(String payload, Long... correct) {
        return new GradingInput(null, Set.of(correct), payload == null ? null : j(payload));
    }

    @Test
    void multiChoicePartialUsesD44Formula() {
        var input = choices(null, 1L, 2L, 3L);
        assertTrue(multi.isCorrect(input, j("{\"choiceIds\":[3,1,2]}")));
        assertCredit("1", multi.credit(input, j("{\"choiceIds\":[1,2,3]}")));
        assertCredit("0.6667", multi.credit(input, j("{\"choiceIds\":[1,2]}"))); // thiếu một
        assertCredit("0.5000", multi.credit(input, j("{\"choiceIds\":[1,2,3,9,8,7]}"))); // thừa: 3 ÷ 6
        assertCredit("0.3333", multi.credit(input, j("{\"choiceIds\":[1,9]}")));
        assertCredit("0", multi.credit(input, j("{\"choiceIds\":[9]}")));
        assertCredit("0.3333", multi.credit(input, j("{\"choiceIds\":[1,1,1]}"))); // trùng chỉ tính một lần
        assertFalse(multi.isCorrect(input, j("{\"choiceIds\":[1,2]}")));
    }

    @Test
    void multiChoiceAllMode() {
        var input = choices("{\"scoring\":\"ALL\"}", 1L, 2L);
        assertCredit("1", multi.credit(input, j("{\"choiceIds\":[2,1]}")));
        assertCredit("0", multi.credit(input, j("{\"choiceIds\":[1]}")));
        assertCredit("0", multi.credit(input, j("{\"choiceIds\":[1,2,3]}")));
    }

    @Test
    void multiChoiceEmptyOrMalformedIsZero() {
        var input = choices(null, 1L, 2L);
        for (String bad : new String[] {"{}", "{\"choiceIds\":[]}", "{\"choiceIds\":\"1\"}", "{\"choiceIds\":[\"x\"]}",
                "{\"choiceIds\":[1.5]}", "{\"choiceIds\":null}", "[]"}) {
            assertFalse(multi.isCorrect(input, j(bad)), bad);
            assertCredit("0", multi.credit(input, j(bad)));
        }
        assertFalse(multi.isCorrect(choices(null), j("{\"choiceIds\":[]}"))); // câu không có đáp án đúng
        assertEquals("{\"choiceIds\":[1,2]}", multi.correctAnswer(input).toString());
    }

    // ---- TRUE_FALSE ----

    private final TrueFalseGrader tf = new TrueFalseGrader();
    private static final String TFNG = "{\"options\":[\"TRUE\",\"FALSE\",\"NOT_GIVEN\"]}";

    @Test
    void trueFalse() {
        var input = in(TFNG, "{\"answer\":\"NOT_GIVEN\"}");
        assertTrue(tf.isCorrect(input, j("{\"value\":\"NOT_GIVEN\"}")));
        assertTrue(tf.isCorrect(input, j("{\"value\":\" not_given \"}")));
        assertFalse(tf.isCorrect(input, j("{\"value\":\"TRUE\"}")));
        assertFalse(tf.isCorrect(input, j("{\"value\":\"YES\"}"))); // ngoài tập lựa chọn
        assertFalse(tf.isCorrect(input, j("{}")));
        assertFalse(tf.isCorrect(input, j("{\"value\":true}")));
        assertFalse(tf.isCorrect(input, j("{\"value\":null}")));
        assertFalse(tf.isCorrect(in(null, "{\"answer\":\"TRUE\"}"), j("{\"value\":\"TRUE\"}")));
    }

    // ---- FILL_TEXT ----

    private final FillTextGrader fill = new FillTextGrader();

    @Test
    void fillTextCaseAndWhitespaceInsensitiveWithManyAccepted() {
        var input = in("{\"maxWords\":2}", "{\"accepted\":[\"Ice cream\",\"gelato\"]}");
        assertTrue(fill.isCorrect(input, j("{\"text\":\"ice cream\"}")));
        assertTrue(fill.isCorrect(input, j("{\"text\":\"  ICE    Cream \"}")));
        assertTrue(fill.isCorrect(input, j("{\"text\":\"Gelato\"}")));
        assertFalse(fill.isCorrect(input, j("{\"text\":\"ice-cream\"}")));
        assertFalse(fill.isCorrect(input, j("{\"text\":\"\"}")));
        assertFalse(fill.isCorrect(input, j("{\"text\":\"   \"}")));
        assertFalse(fill.isCorrect(input, j("{\"text\":42}")));
        assertFalse(fill.isCorrect(input, j("{}")));
    }

    @Test
    void fillTextWordLimitRejectsLongAnswers() {
        var input = in("{\"maxWords\":1}", "{\"accepted\":[\"gelato\",\"ice cream\"]}");
        assertTrue(fill.isCorrect(input, j("{\"text\":\"gelato\"}")));
        assertFalse(fill.isCorrect(input, j("{\"text\":\"ice cream\"}"))); // đúng đáp án nhưng vượt giới hạn
        assertTrue(fill.isCorrect(in(null, "{\"accepted\":[\"ice cream\"]}"), j("{\"text\":\"ice cream\"}"))); // không giới hạn
    }

    // ---- WORD_BANK ----

    private final WordBankGrader bank = new WordBankGrader();
    private static final String BANK_KEY = "{\"blanks\":{\"1\":\"cat\",\"2\":\"Dog\"}}";

    @Test
    void wordBank() {
        var input = in(null, BANK_KEY);
        assertTrue(bank.isCorrect(input, j("{\"blanks\":{\"2\":\" dog \",\"1\":\"CAT\"}}")));
        assertFalse(bank.isCorrect(input, j("{\"blanks\":{\"1\":\"cat\"}}"))); // thiếu chỗ trống
        assertFalse(bank.isCorrect(input, j("{\"blanks\":{\"1\":\"cat\",\"2\":\"dog\",\"3\":\"x\"}}"))); // thừa
        assertFalse(bank.isCorrect(input, j("{\"blanks\":{\"1\":\"dog\",\"2\":\"cat\"}}"))); // đảo
        assertFalse(bank.isCorrect(input, j("{\"blanks\":{\"1\":\"cat\",\"2\":5}}")));
        assertFalse(bank.isCorrect(input, j("{\"blanks\":[]}")));
        assertFalse(bank.isCorrect(input, j("{}")));
    }

    // ---- MATCH_OPTIONS ----

    private final MatchOptionsGrader match = new MatchOptionsGrader();

    @Test
    void matchOptionsAllowsRepeatedOptions() {
        var input = in(null, "{\"matches\":{\"q1\":\"A\",\"q2\":\"A\",\"q3\":\"C\"}}");
        assertTrue(match.isCorrect(input, j("{\"matches\":{\"q3\":\"C\",\"q2\":\"A\",\"q1\":\"A\"}}")));
        assertFalse(match.isCorrect(input, j("{\"matches\":{\"q1\":\"A\",\"q2\":\"B\",\"q3\":\"C\"}}")));
        assertFalse(match.isCorrect(input, j("{\"matches\":{\"q1\":\"A\",\"q2\":\"A\"}}")));
        assertFalse(match.isCorrect(input, j("{\"matches\":{\"q1\":\"A\",\"q2\":\"A\",\"q3\":\"C\",\"q4\":\"D\"}}")));
        assertFalse(match.isCorrect(input, j("{\"matches\":{\"q1\":[\"A\"],\"q2\":\"A\",\"q3\":\"C\"}}")));
        assertFalse(match.isCorrect(input, j("{\"matches\":\"x\"}")));
        assertFalse(match.isCorrect(input, j("{}")));
    }

    // ---- D-140: WORD_BANK / MATCH_OPTIONS chấm từng phần (mặc định) hoặc ALL ----

    @Test
    void wordBankPartialIsDefaultAndExtraKeysNeverRaiseCredit() {
        var input = in(null, BANK_KEY);
        assertCredit("1", bank.credit(input, j("{\"blanks\":{\"1\":\"cat\",\"2\":\"dog\"}}")));
        assertCredit("0.5", bank.credit(input, j("{\"blanks\":{\"1\":\"cat\",\"2\":\"x\"}}")));
        assertCredit("0.5", bank.credit(input, j("{\"blanks\":{\"1\":\"cat\"}}"))); // thiếu chỗ trống
        assertCredit("0.6667", bank.credit(input, j("{\"blanks\":{\"1\":\"cat\",\"2\":\"dog\",\"3\":\"x\"}}"))); // thừa: 2 ÷ 3
        assertCredit("0", bank.credit(input, j("{\"blanks\":{\"1\":\"dog\",\"2\":\"cat\"}}")));
        assertCredit("0", bank.credit(input, j("{\"blanks\":[]}")));
        assertFalse(Credit.isFull(bank.credit(input, j("{\"blanks\":{\"1\":\"cat\",\"2\":\"dog\",\"3\":\"x\"}}"))));
    }

    @Test
    void wordBankAllModeIsAllOrNothing() {
        var input = in("{\"scoring\":\"ALL\"}", BANK_KEY);
        assertCredit("1", bank.credit(input, j("{\"blanks\":{\"1\":\"cat\",\"2\":\"dog\"}}")));
        assertCredit("0", bank.credit(input, j("{\"blanks\":{\"1\":\"cat\",\"2\":\"x\"}}")));
    }

    @Test
    void matchOptionsPartialIsDefaultAndExtraKeysNeverRaiseCredit() {
        var input = in(null, "{\"matches\":{\"q1\":\"A\",\"q2\":\"A\",\"q3\":\"C\",\"q4\":\"D\"}}");
        assertCredit("1", match.credit(input, j("{\"matches\":{\"q1\":\"A\",\"q2\":\"A\",\"q3\":\"C\",\"q4\":\"D\"}}")));
        assertCredit("0.75", match.credit(input, j("{\"matches\":{\"q1\":\"A\",\"q2\":\"B\",\"q3\":\"C\",\"q4\":\"D\"}}")));
        assertCredit("0.5", match.credit(input, j("{\"matches\":{\"q1\":\"A\",\"q2\":\"A\"}}")));
        assertCredit("0.8", match.credit(input, j("{\"matches\":{\"q1\":\"A\",\"q2\":\"A\",\"q3\":\"C\",\"q4\":\"D\",\"q5\":\"E\"}}"))); // 4 ÷ 5
        assertCredit("0", match.credit(input, j("{\"matches\":\"x\"}")));
    }

    @Test
    void matchOptionsAllModeIsAllOrNothing() {
        var input = in("{\"scoring\":\"ALL\"}", "{\"matches\":{\"q1\":\"A\",\"q2\":\"B\"}}");
        assertCredit("1", match.credit(input, j("{\"matches\":{\"q1\":\"A\",\"q2\":\"B\"}}")));
        assertCredit("0", match.credit(input, j("{\"matches\":{\"q1\":\"A\",\"q2\":\"A\"}}")));
    }

    @Test
    void registryRoutesEveryNewTypeAndSwallowsGraderErrors() {
        var registry = new GraderRegistry(java.util.List.of(multi, tf, fill, bank, match));
        for (var t : java.util.List.of(com.pps.smartenglish.exercise.domain.QuestionType.CHOICE_MULTI,
                com.pps.smartenglish.exercise.domain.QuestionType.TRUE_FALSE, com.pps.smartenglish.exercise.domain.QuestionType.FILL_TEXT,
                com.pps.smartenglish.exercise.domain.QuestionType.WORD_BANK, com.pps.smartenglish.exercise.domain.QuestionType.MATCH_OPTIONS)) {
            assertTrue(registry.isAutoGraded(t), t.name());
        }
        // answerKey null (dữ liệu hỏng) không được làm sập luồng nộp bài
        assertCredit("0", registry.credit(com.pps.smartenglish.exercise.domain.QuestionType.FILL_TEXT,
                new GradingInput(null, Set.of()), j("{\"text\":\"a\"}")));
    }
}

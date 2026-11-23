package com.pps.smartenglish.exercise.grading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Tester #67: giá trị biên và đầu vào lạ của 5 grader mới (bổ sung NewGradersTest). */
class NewGradersEdgeTest {

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

    private final MultiChoiceGrader multi = new MultiChoiceGrader();
    private final TrueFalseGrader tf = new TrueFalseGrader();
    private final FillTextGrader fill = new FillTextGrader();
    private final WordBankGrader words = new WordBankGrader();
    private final MatchOptionsGrader match = new MatchOptionsGrader();

    @Test
    void blankAnswerIsWrongForEveryNewType() {
        JsonNode blank = MissingNode.getInstance();
        var choices = new GradingInput(null, Set.of(1L), null);
        assertCredit("0", multi.credit(choices, blank));
        assertFalse(multi.isCorrect(choices, blank));
        assertFalse(tf.isCorrect(in("{\"options\":[\"TRUE\",\"FALSE\"]}", "{\"answer\":\"TRUE\"}"), blank));
        assertFalse(fill.isCorrect(in(null, "{\"accepted\":[\"x\"]}"), blank));
        assertFalse(words.isCorrect(in(null, "{\"blanks\":{\"1\":\"cat\"}}"), blank));
        assertFalse(match.isCorrect(in(null, "{\"matches\":{\"i\":\"A\"}}"), blank));
    }

    @Test
    void multiChoiceEmptySelectionAndSelectAllAreNotRewarded() {
        var input = new GradingInput(null, Set.of(1L, 2L), null);
        assertCredit("0", multi.credit(input, j("{\"choiceIds\":[]}")));
        // chọn hết 4 lựa chọn khi 2 đúng: 2 ÷ 4
        assertCredit("0.5", multi.credit(input, j("{\"choiceIds\":[1,2,3,4]}")));
        // id dạng chuỗi hay số thực là sai định dạng: 0 điểm, không ném lỗi
        assertCredit("0", multi.credit(input, j("{\"choiceIds\":[\"1\",2]}")));
        assertCredit("0", multi.credit(input, j("{\"choiceIds\":[1.0,2]}")));
        assertCredit("0", multi.credit(input, j("{\"choiceIds\":{\"0\":1}}")));
        // ALL viết thường không phải ALL: chấm từng phần (khớp validator chỉ nhận PARTIAL|ALL)
        var lower = new GradingInput(null, Set.of(1L, 2L), j("{\"scoring\":\"all\"}"));
        assertCredit("0.5", multi.credit(lower, j("{\"choiceIds\":[1]}")));
    }

    @Test
    void creditAndIsCorrectAgreeForMultiChoice() {
        var partial = new GradingInput(null, Set.of(1L, 2L, 3L), null);
        var all = new GradingInput(null, Set.of(1L, 2L, 3L), j("{\"scoring\":\"ALL\"}"));
        for (String a : new String[] {"[]", "[1]", "[1,2]", "[1,2,3]", "[3,2,1,1]", "[1,2,3,4]", "[4]"}) {
            JsonNode ans = j("{\"choiceIds\":" + a + "}");
            for (var input : new GradingInput[] {partial, all}) {
                assertEquals(multi.isCorrect(input, ans), Credit.isFull(multi.credit(input, ans)), a);
            }
        }
    }

    @Test
    void trueFalseRejectsValuesOutsideOptionsAndNonStrings() {
        var input = in("{\"options\":[\"YES\",\"NO\",\"NOT_GIVEN\"]}", "{\"answer\":\"NO\"}");
        assertTrue(tf.isCorrect(input, j("{\"value\":\"  no \"}")));
        assertFalse(tf.isCorrect(input, j("{\"value\":false}")));
        assertFalse(tf.isCorrect(input, j("{\"value\":[\"NO\"]}")));
        assertFalse(tf.isCorrect(input, j("\"NO\"")));
        assertFalse(tf.isCorrect(input, j("{\"value\":\"NOT GIVEN\"}")));
    }

    @Test
    void fillTextWordLimitBoundaryAndWhitespaceKinds() {
        var two = in("{\"maxWords\":2}", "{\"accepted\":[\"ice cream\"]}");
        assertTrue(fill.isCorrect(two, j("{\"text\":\"ice\\tcream\"}"))); // tab cũng là khoảng trắng
        assertTrue(fill.isCorrect(two, j("{\"text\":\"ice\\n cream\"}")));
        assertFalse(fill.isCorrect(two, j("{\"text\":\"\"}")));
        assertFalse(fill.isCorrect(two, j("{\"text\":\"   \"}")));
        assertFalse(fill.isCorrect(two, j("{\"text\":123}")));
        // đúng biên: 2 từ được, 3 từ thì sai dù có đáp án
        var accepted3 = in("{\"maxWords\":2}", "{\"accepted\":[\"ice cream\",\"an ice cream\"]}");
        assertFalse(fill.isCorrect(accepted3, j("{\"text\":\"an ice cream\"}")));
    }

    @Test
    void fillTextTreatsNonBreakingSpaceAsWhitespace() {
        // NBSP (U+00A0) hay gặp khi dán chữ từ web/Word; "gộp khoảng trắng" phải gồm cả nó
        var input = in(null, "{\"accepted\":[\"ice cream\"]}");
        assertTrue(fill.isCorrect(input, j("{\"text\":\"ice\\u00A0cream\"}")));
        assertTrue(fill.isCorrect(input, j("{\"text\":\"\\u00A0ice cream\\u00A0\"}")));
    }

    @Test
    void wordBankAndMatchRejectWrongShapes() {
        var wb = in(null, "{\"blanks\":{\"1\":\"cat\",\"2\":\"mouse\"}}");
        assertTrue(words.isCorrect(wb, j("{\"blanks\":{\"2\":\" MOUSE \",\"1\":\"Cat\"}}")));
        assertFalse(words.isCorrect(wb, j("{\"blanks\":{\"1\":\"cat\",\"3\":\"mouse\"}}"))); // khóa lạ thay khóa thiếu
        assertFalse(words.isCorrect(wb, j("{\"blanks\":{\"1\":\"cat\",\"2\":null}}")));
        assertFalse(words.isCorrect(wb, j("{\"blanks\":[\"cat\",\"mouse\"]}")));
        assertFalse(words.isCorrect(in(null, "{\"blanks\":{}}"), j("{\"blanks\":{}}"))); // key rỗng không bao giờ đúng

        var mo = in(null, "{\"matches\":{\"i1\":\"A\",\"i2\":\"B\"}}");
        assertTrue(match.isCorrect(mo, j("{\"matches\":{\"i2\":\"B\",\"i1\":\"A\"}}")));
        assertFalse(match.isCorrect(mo, j("{\"matches\":{\"i1\":\"a\",\"i2\":\"B\"}}"))); // id phân biệt hoa thường
        assertFalse(match.isCorrect(mo, j("{\"matches\":{\"i1\":{\"id\":\"A\"},\"i2\":\"B\"}}")));
        assertFalse(match.isCorrect(mo, j("{\"matches\":{\"i1\":\"A\",\"i2\":\"B\",\"i3\":\"C\"}}")));
    }
}

package com.pps.smartenglish.exercise.grading;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** D-154, D-157: mỗi kiểu mới một grader; đúng, sai, hoa thường, maxWords, dấu câu của chép chính tả. */
class NewTypeGradersTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode j(String s) {
        try {
            return s == null ? null : JSON.readTree(s.replace('\'', '"'));
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static GradingInput in(String payload, String key) {
        return new GradingInput(j(key), Set.of(), j(payload));
    }

    private static boolean grade(QuestionGrader g, String payload, String key, String answer) {
        return g.isCorrect(in(payload, key), j(answer));
    }

    @Test
    void letterOrder() {
        var g = new LetterOrderGrader();
        String p = "{'letters':[{'id':'a','ch':'t'},{'id':'b','ch':'C'},{'id':'c','ch':'a'}]}";
        String k = "{'word':'Cat'}";
        assertTrue(grade(g, p, k, "{'order':['b','c','a']}"));
        assertFalse(grade(g, p, k, "{'order':['a','b','c']}"));
        assertFalse(grade(g, p, k, "{'order':['b','c']}")); // thiếu chữ
        assertFalse(grade(g, p, k, "{'order':['b','b','a']}")); // dùng một chữ hai lần
        assertFalse(grade(g, p, k, "{'order':['b','c','zz']}")); // id lạ
        assertFalse(grade(g, p, k, "{'order':'bca'}"));
        assertFalse(g.isCorrect(in(p, k), MissingNode.getInstance()));
    }

    @Test
    void dictationIgnoresCaseSpacesAndPunctuation() {
        var g = new NewTypeGraders.Dictation();
        String k = "{'accepted':['It is a nice day.']}";
        assertTrue(grade(g, null, k, "{'text':'it is a nice day'}"));
        assertTrue(grade(g, null, k, "{'text':'  IT  is a, nice day!  '}"));
        assertTrue(grade(g, null, k, "{'text':'It is a nice day.'}"));
        assertFalse(grade(g, null, k, "{'text':'It is nice day'}"));
        assertFalse(grade(g, null, k, "{'text':''}"));
        assertFalse(grade(g, "{'maxWords':3}", k, "{'text':'It is a nice day'}")); // vượt maxWords
        assertTrue(grade(g, "{'maxWords':5}", k, "{'text':'It is a nice day'}"));
        assertFalse(grade(g, null, k, "{'text':'" + "a".repeat(TypedText.MAX_CHARS + 1) + "'}")); // trần độ dài
        assertFalse(g.isCorrect(in(null, k), MissingNode.getInstance()));
    }

    @Test
    void typedTextTypesKeepPunctuationButNotCase() {
        QuestionGrader[] graders = {new NewTypeGraders.ListenForm(), new NewTypeGraders.Sentence(), new NewTypeGraders.Summary(),
                new NewTypeGraders.Note(), new NewTypeGraders.Table(), new NewTypeGraders.Flowchart(), new NewTypeGraders.Form(),
                new NewTypeGraders.Diagram(), new NewTypeGraders.Short()};
        String k = "{'accepted':['the dam','Dam']}";
        for (var g : graders) {
            assertTrue(grade(g, "{'maxWords':2}", k, "{'text':'  THE   Dam '}"), g.type().name());
            assertTrue(grade(g, "{'maxWords':2}", k, "{'text':'dam'}"));
            assertFalse(grade(g, "{'maxWords':2}", k, "{'text':'the old dam'}"));
            assertFalse(grade(g, "{'maxWords':1}", k, "{'text':'the dam'}")); // đúng đáp án nhưng vượt maxWords
            assertFalse(grade(g, null, k, "{'text':'dam.'}")); // dấu câu không bị bỏ ở IELTS
            assertFalse(grade(g, null, k, "{'text':5}"));
            assertFalse(g.isCorrect(in(null, k), MissingNode.getInstance()));
        }
    }

    @Test
    void pickTypes() {
        QuestionGrader[] options = {new NewTypeGraders.MatchInfo(), new NewTypeGraders.MatchHeadings(), new NewTypeGraders.MatchFeatures(),
                new NewTypeGraders.MatchEndings(), new NewTypeGraders.Matching(), new NewTypeGraders.MapLabel()};
        for (var g : options) {
            assertTrue(grade(g, null, "{'option':'iv'}", "{'option':' IV '}"), g.type().name());
            assertFalse(grade(g, null, "{'option':'iv'}", "{'option':'v'}"));
            assertFalse(grade(g, null, "{'option':'iv'}", "{'word':'iv'}"));
            assertFalse(grade(g, null, "{'option':'iv'}", "{'option':4}"));
            assertFalse(g.isCorrect(in(null, "{'option':'iv'}"), MissingNode.getInstance()));
        }
        assertTrue(grade(new NewTypeGraders.Tfng(), null, "{'value':'NOT_GIVEN'}", "{'value':'not_given'}"));
        assertFalse(grade(new NewTypeGraders.Tfng(), null, "{'value':'NOT_GIVEN'}", "{'value':'FALSE'}"));
        assertTrue(grade(new NewTypeGraders.Ynng(), null, "{'value':'YES'}", "{'value':'yes'}"));
        assertTrue(grade(new NewTypeGraders.SummaryBank(), null, "{'word':'w2'}", "{'word':'W2'}"));
        assertFalse(grade(new NewTypeGraders.SummaryBank(), null, "{'word':'w2'}", "{'word':'w3'}"));
    }
}

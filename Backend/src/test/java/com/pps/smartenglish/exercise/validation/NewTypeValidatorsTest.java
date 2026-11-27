package com.pps.smartenglish.exercise.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pps.smartenglish.shared.error.ApiException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** D-154, D-157: mỗi kiểu mới một validator; hợp lệ và không hợp lệ. */
class NewTypeValidatorsTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode j(String s) {
        try {
            return s == null ? null : JSON.readTree(s.replace('\'', '"'));
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static void ok(QuestionValidator v, String payload, String key) {
        assertDoesNotThrow(() -> v.validate(new QuestionDraft(j(payload), j(key), List.of()), Set.of()));
    }

    private static void bad(QuestionValidator v, String payload, String key) {
        assertThrows(ApiException.class, () -> v.validate(new QuestionDraft(j(payload), j(key), List.of()), Set.of()));
    }

    @Test
    void letterOrder() {
        var v = new LetterOrderValidator();
        String p = "{'letters':[{'id':'1','ch':'c'},{'id':'2','ch':'A'},{'id':'3','ch':'t'}]}";
        ok(v, p, "{'word':'cat'}");
        ok(v, p, "{'word':'TAC'}"); // không phân biệt hoa thường, thứ tự tùy ý
        bad(v, p, "{'word':'cab'}"); // chữ lạ
        bad(v, p, "{'word':'ca'}"); // thiếu chữ
        bad(v, "{'letters':[{'id':'1','ch':'c'}]}", "{'word':'c'}"); // dưới 2 chữ
        bad(v, "{'letters':[{'id':'1','ch':'c'},{'id':'1','ch':'a'}]}", "{'word':'ca'}"); // id lặp
        bad(v, "{'letters':[{'id':'1','ch':'ca'},{'id':'2','ch':'t'}]}", "{'word':'cat'}"); // ch dài hơn một ký tự
        bad(v, null, "{'word':'cat'}");
    }

    @Test
    void dictation() {
        var v = new NewTypeValidators.Dictation();
        ok(v, null, "{'accepted':['I am here.']}");
        ok(v, "{'maxWords':4}", "{'accepted':['I am here.']}");
        bad(v, "{'maxWords':2}", "{'accepted':['I am here.']}"); // vượt maxWords
        bad(v, null, "{'accepted':['...']}"); // rỗng sau khi bỏ dấu câu
        bad(v, "{'maxWords':0}", "{'accepted':['x']}");
        bad(v, "{'slot':'a'}", "{'accepted':['x']}"); // trường lạ
        bad(v, null, "{'accepted':[]}");
    }

    @Test
    void textSlotTypes() {
        QuestionValidator[] slotTypes = {new NewTypeValidators.ListenForm(), new NewTypeValidators.Summary(), new NewTypeValidators.Note(),
                new NewTypeValidators.Table(), new NewTypeValidators.Flowchart(), new NewTypeValidators.Form()};
        for (var v : slotTypes) {
            ok(v, "{'slot':'q1'}", "{'accepted':['dam']}");
            ok(v, "{'slot':'q1','maxWords':2}", "{'accepted':['the dam','dam']}");
            bad(v, "{'slot':'q1','maxWords':1}", "{'accepted':['the dam']}");
            bad(v, null, "{'accepted':['dam']}"); // thiếu slot
            bad(v, "{'slot':'q1'}", "{'accepted':[]}");
            bad(v, "{'slot':'q1'}", "{'answer':'dam'}");
        }
    }

    @Test
    void sentenceDiagramAndShort() {
        var s = new NewTypeValidators.Sentence();
        ok(s, "{'before':'The dam was built in','after':'.'}", "{'accepted':['1990']}");
        ok(s, "{'after':'is the capital'}", "{'accepted':['Paris']}");
        bad(s, "{'maxWords':2}", "{'accepted':['x']}"); // thiếu cả before và after
        bad(s, null, "{'accepted':['x']}");
        var d = new NewTypeValidators.Diagram();
        ok(d, "{'slot':'p1','description':'Label the valve'}", "{'accepted':['valve']}");
        bad(d, "{'slot':'p1'}", "{'accepted':['valve']}"); // thiếu description
        var sh = new NewTypeValidators.Short();
        ok(sh, "{'question':'Who built it?'}", "{'accepted':['the Romans']}");
        bad(sh, "{'question':''}", "{'accepted':['x']}");
    }

    @Test
    void fixedChoiceTypes() {
        var tf = new NewTypeValidators.Tfng();
        ok(tf, "{'statement':'The sky is blue.'}", "{'value':'NOT_GIVEN'}");
        bad(tf, "{'statement':'x'}", "{'value':'YES'}");
        bad(tf, "{'statement':'x'}", "{'value':'true'}"); // phải đúng dạng viết hoa của đặc tả
        bad(tf, null, "{'value':'TRUE'}");
        var yn = new NewTypeValidators.Ynng();
        ok(yn, "{'statement':'x'}", "{'value':'NO'}");
        bad(yn, "{'statement':'x'}", "{'value':'TRUE'}");
    }

    @Test
    void layoutChoiceTypes() {
        Object[][] cases = {{new NewTypeValidators.MatchInfo(), "statement"}, {new NewTypeValidators.MatchHeadings(), "paragraph"},
                {new NewTypeValidators.MatchFeatures(), "statement"}, {new NewTypeValidators.MatchEndings(), "stem"},
                {new NewTypeValidators.Matching(), "statement"}, {new NewTypeValidators.MapLabel(), "label"}};
        for (Object[] c : cases) {
            var v = (QuestionValidator) c[0];
            String p = "{'" + c[1] + "':'text'}";
            ok(v, p, "{'option':'B'}");
            bad(v, p, "{'option':''}");
            bad(v, p, "{'word':'B'}"); // sai tên trường đáp án
            bad(v, "{'other':'text'}", "{'option':'B'}");
        }
        var bank = new NewTypeValidators.SummaryBank();
        ok(bank, "{'slot':'q1'}", "{'word':'w3'}");
        bad(bank, "{'slot':'q1'}", "{'option':'w3'}");
        bad(bank, "{}", "{'word':'w3'}");
    }
}

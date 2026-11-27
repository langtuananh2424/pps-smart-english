package com.pps.smartenglish.exercise.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pps.smartenglish.shared.error.ApiException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** D-154 mục 4: payload {prompt, maxSeconds?}, không có answerKey. */
class ListenRecordValidatorTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final ListenRecordValidator v = new ListenRecordValidator();

    private static JsonNode j(String s) {
        try {
            return s == null ? null : JSON.readTree(s.replace('\'', '"'));
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private void ok(String payload, String key) {
        assertDoesNotThrow(() -> v.validate(new QuestionDraft(j(payload), j(key), List.of()), Set.of()));
    }

    private void bad(String payload, String key) {
        assertThrows(ApiException.class, () -> v.validate(new QuestionDraft(j(payload), j(key), List.of()), Set.of()));
    }

    @Test
    void acceptsPromptWithOptionalSeconds() {
        ok("{'prompt':'Repeat what you heard.'}", null);
        ok("{'prompt':'Repeat.','maxSeconds':90}", null);
    }

    @Test
    void rejectsBadPayload() {
        bad(null, null);
        bad("{}", null); // thiếu prompt
        bad("{'prompt':''}", null);
        bad("{'prompt':'x','maxSeconds':5}", null); // dưới 10
        bad("{'prompt':'x','maxSeconds':181}", null); // trên 180
        bad("{'prompt':'x','part':1}", null); // trường lạ
        bad("{'prompt':'x'}", "{'a':1}"); // có answerKey
    }
}

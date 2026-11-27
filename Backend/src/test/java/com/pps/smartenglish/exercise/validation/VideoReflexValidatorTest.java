package com.pps.smartenglish.exercise.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pps.smartenglish.exercise.domain.QuestionType;
import com.pps.smartenglish.shared.error.ApiException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** D-154 mục 5, D-158: payload {at, prompt, maxSeconds?}; CUE thêm cue, DISCUSS thêm instruction; không có answerKey. */
class VideoReflexValidatorTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final QuestionValidator shortQ = new VideoReflexValidators.ShortAnswer();
    private final QuestionValidator cue = new VideoReflexValidators.Cue();
    private final QuestionValidator discuss = new VideoReflexValidators.Discuss();
    private final QuestionValidator picture = new VideoReflexValidators.Picture();

    private static JsonNode j(String s) {
        try {
            return s == null ? null : JSON.readTree(s.replace('\'', '"'));
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static void ok(QuestionValidator v, String payload) {
        assertDoesNotThrow(() -> v.validate(new QuestionDraft(j(payload), null, List.of()), Set.of()));
    }

    private static void bad(QuestionValidator v, String payload, String key) {
        assertThrows(ApiException.class, () -> v.validate(new QuestionDraft(j(payload), j(key), List.of()), Set.of()));
    }

    @Test
    void typesMatch() {
        org.junit.jupiter.api.Assertions.assertEquals(QuestionType.VIDEO_SHORT, shortQ.type());
        org.junit.jupiter.api.Assertions.assertEquals(QuestionType.VIDEO_CUE, cue.type());
        org.junit.jupiter.api.Assertions.assertEquals(QuestionType.VIDEO_DISCUSS, discuss.type());
        org.junit.jupiter.api.Assertions.assertEquals(QuestionType.VIDEO_PICTURE, picture.type());
    }

    @Test
    void acceptsEachType() {
        ok(shortQ, "{'at':45,'prompt':'What did you do after school?'}");
        ok(shortQ, "{'at':0,'prompt':'x','maxSeconds':45}");
        ok(cue, "{'at':30,'prompt':'Describe a place.','cue':'Where is it? Why do you like it?'}");
        ok(discuss, "{'at':30,'prompt':'Which do you prefer?','instruction':'Nêu lựa chọn và giải thích.','maxSeconds':90}");
        ok(picture, "{'at':12,'prompt':'Describe the picture.'}");
    }

    @Test
    void rejectsBadPayload() {
        bad(shortQ, null, null);
        bad(shortQ, "{'prompt':'x'}", null); // thiếu at
        bad(shortQ, "{'at':-1,'prompt':'x'}", null);
        bad(shortQ, "{'at':'1:30','prompt':'x'}", null); // at là số giây
        bad(shortQ, "{'at':5}", null); // thiếu prompt
        bad(shortQ, "{'at':5,'prompt':''}", null);
        bad(shortQ, "{'at':5,'prompt':'x','maxSeconds':9}", null);
        bad(shortQ, "{'at':5,'prompt':'x','maxSeconds':181}", null);
        bad(shortQ, "{'at':5,'prompt':'x','cue':'y'}", null); // cue chỉ có ở CUE
        bad(cue, "{'at':5,'prompt':'x'}", null); // thiếu cue
        bad(discuss, "{'at':5,'prompt':'x'}", null); // thiếu instruction
        bad(discuss, "{'at':5,'prompt':'x','cue':'y'}", null);
        bad(picture, "{'at':5,'prompt':'x','image':'a.png'}", null); // ảnh là media của câu, không nằm trong payload
        bad(shortQ, "{'at':5,'prompt':'x'}", "{'a':1}"); // có answerKey
    }
}

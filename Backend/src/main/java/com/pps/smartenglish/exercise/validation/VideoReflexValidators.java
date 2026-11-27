package com.pps.smartenglish.exercise.validation;

import static com.pps.smartenglish.exercise.domain.QuestionType.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * D-154 mục 5, D-158 (Figma 10.28–10.31): Video phản xạ, mỗi câu là một mốc trong video của nhóm. payload chung {at (giây, >= 0),
 * prompt, maxSeconds? 10..180}; CUE thêm cue, DISCUSS thêm instruction; PICTURE có ảnh là media của câu (không nằm trong payload).
 * Không có answerKey. at tăng dần theo thứ tự câu và nhỏ hơn thời lượng video: kiểm chéo ở GroupLayoutCheck vì cần nhóm.
 */
final class VideoReflexValidators {

    private VideoReflexValidators() {}

    abstract static class Base implements QuestionValidator {

        private final QuestionType type;
        private final String extra; // trường bắt buộc riêng của dạng, null nếu không có

        Base(QuestionType type, String extra) {
            this.type = type;
            this.extra = extra;
        }

        @Override
        public QuestionType type() {
            return type;
        }

        @Override
        public void validate(QuestionDraft d, Set<String> grammarCodes) {
            Set<String> allowed = new HashSet<>(Set.of("at", "prompt", "maxSeconds"));
            if (extra != null) {
                allowed.add(extra);
            }
            JsonNode payload = JsonRules.object(d.payload(), "payload", allowed);
            if (JsonRules.integer(payload, "at", "payload") < 0) {
                throw JsonRules.bad("payload.at", "Mốc dừng phải từ 0 giây");
            }
            if (JsonRules.text(payload, "prompt", "payload").length() > SpeakingValidator.MAX_PROMPT) {
                throw JsonRules.bad("payload.prompt", "Tối đa " + SpeakingValidator.MAX_PROMPT + " ký tự");
            }
            if (extra != null && JsonRules.text(payload, extra, "payload").length() > SpeakingValidator.MAX_PROMPT) {
                throw JsonRules.bad("payload." + extra, "Tối đa " + SpeakingValidator.MAX_PROMPT + " ký tự");
            }
            if (payload.has("maxSeconds")) {
                int seconds = JsonRules.integer(payload, "maxSeconds", "payload");
                if (seconds < SpeakingValidator.MIN_SECONDS || seconds > SpeakingValidator.MAX_SECONDS) {
                    throw JsonRules.bad("payload.maxSeconds", "Từ " + SpeakingValidator.MIN_SECONDS + " đến " + SpeakingValidator.MAX_SECONDS + " giây");
                }
            }
            JsonRules.requireNone(d.answerKey(), "answerKey", "Câu video phản xạ không có answerKey");
        }
    }

    @Component
    static class ShortAnswer extends Base { ShortAnswer() { super(VIDEO_SHORT, null); } }

    @Component
    static class Cue extends Base { Cue() { super(VIDEO_CUE, "cue"); } }

    @Component
    static class Discuss extends Base { Discuss() { super(VIDEO_DISCUSS, "instruction"); } }

    @Component
    static class Picture extends Base { Picture() { super(VIDEO_PICTURE, null); } }
}

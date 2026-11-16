package com.pps.smartenglish.ai.speaking;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pps.smartenglish.ai.dto.SpeakingGrade.Suggestion;
import com.pps.smartenglish.ai.speaking.SpeakingScorer.Fluency;
import com.pps.smartenglish.ai.speaking.SpeakingScorer.WrongWord;
import java.util.List;

/**
 * Output của job SPEAKING_GRADE, cũng là speaking_submissions.result. status GRADED: đủ các trường chấm;
 * UNCLEAR: chỉ có reason (too_short, too_few_words, low_confidence, too_slow), học viên được hoàn lượt và nên thu lại.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SpeakingResult(String status, String reason, String transcript, Bands bands, Double overall, Fluency fluency,
                             Double pronunciationScore, List<WrongWord> mispronounced, String feedback, List<Suggestion> suggestions) {

    public record Bands(double pronunciation, double fluency, double lexical, double grammar) {}

    public static SpeakingResult unclear(String reason) {
        return new SpeakingResult(SpeakingSubmission.UNCLEAR, reason, null, null, null, null, null, null, null, null);
    }
}

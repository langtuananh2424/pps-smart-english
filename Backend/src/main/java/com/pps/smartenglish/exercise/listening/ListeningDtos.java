package com.pps.smartenglish.exercise.listening;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** FR-EX-10, D-135, D-136: DTO âm thanh, luật Nghe ép và kết quả Nghe ép. */
public final class ListeningDtos {

    private ListeningDtos() {}

    /** Audio của CMS: không có url (nghe thử qua phát lượt như học viên), không lộ key. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AudioFile(Long id, String mime, long sizeBytes, Integer durationMs) {}

    public record AudioComplete(Integer durationMs) {}

    public record ThresholdInput(@NotNull Long groupId, @NotNull @Min(1) @Max(100) Integer passPct) {}

    /**
     * Trường null = giữ giá trị hiện có (hoặc mặc định cấu hình). Ngưỡng đạt chọn MỘT trong hai (D-142): passPctTotal (cả bài) hoặc
     * groupThresholds khác rỗng (theo part), đặt cả hai thì 400 threshold_conflict. Đặt kiểu này thì kiểu kia bị bỏ; groupThresholds
     * null và passPctTotal null = giữ nguyên; danh sách rỗng = bỏ ngưỡng theo part (về cả bài).
     */
    public record RuleInput(@Min(1) Integer playLimit, @Min(1) @Max(100) Integer passPctTotal, @Min(1) Integer hintEveryN,
                            @Min(1) Integer wrongThreshold, @Size(max = 50) List<@NotNull @Valid ThresholdInput> groupThresholds) {}

    /**
     * ruleId null = chưa có dòng luật, đang dùng mặc định cấu hình. thresholdMode TOTAL: đạt theo passPctTotal; PART: đạt khi mọi part
     * trong groupThresholds đạt, passPctTotal bị bỏ qua (D-142).
     */
    public record RuleView(Long ruleId, int playLimit, int passPctTotal, int hintEveryN, int wrongThreshold,
                           List<ThresholdInput> groupThresholds, String thresholdMode) {

        public boolean byPart() {
            return !groupThresholds.isEmpty();
        }

        public Integer thresholdOf(Long groupId) {
            return groupThresholds.stream().filter(t -> t.groupId().equals(groupId)).map(ThresholdInput::passPct).findFirst().orElse(null);
        }
    }

    /** playNo, remaining null khi chế độ không đếm (FREE) hoặc không giới hạn. url ký ngắn hạn, không lộ key (NFR-SEC-02). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PlayGrant(String playbackMode, Integer playNo, Integer playLimit, Integer remaining, String url, Instant expiresAt) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PlayState(int playNo, boolean completed, Integer playLimit, Integer remaining) {}

    /**
     * Kết quả chế độ Nghe ép: chỉ số câu đúng, không có đáp án từng câu. hardQuestions null = chưa đến lượt hiện danh sách
     * (mỗi hintEveryN lượt làm); có mặt (kể cả rỗng) thì học viên chọn câu xem script.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ForcedResult(int correctCount, int totalCount, int percent, Integer passPct, boolean passed, List<PartResult> parts,
                               int attemptNo, int hintEveryN, List<HardQuestion> hardQuestions) {}

    /** passPct, passed chỉ có khi luật theo part (D-142). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PartResult(Long groupId, String title, int correct, int total, Integer passPct, Boolean passed) {}

    /** D-143: tiến độ Nghe ép của một người nhận bài giao (cho giáo viên), không có điểm (D-142). */
    public record ForcedProgress(int attemptCount, long latestAttemptId, int latestCorrect, int latestTotal, boolean latestPassed,
                                 boolean everPassed) {}

    public record HardQuestion(Long questionId, String prompt, long wrongCount) {}

    /** Đúng một trong text (script của câu) hoặc html (script của nhóm, đã làm sạch). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ScriptView(Long questionId, String text, String html) {}
}

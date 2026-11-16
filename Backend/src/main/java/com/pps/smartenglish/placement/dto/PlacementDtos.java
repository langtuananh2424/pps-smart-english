package com.pps.smartenglish.placement.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.dto.BankResponse.ChoiceView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** DTO kiểm tra đầu vào. Không DTO nào của học viên/khách mang đáp án đúng. */
public final class PlacementDtos {

    private PlacementDtos() {}

    // ---- người làm bài ----

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QuestionOut(Long id, String skill, String type, String prompt, JsonNode payload, List<ChoiceView> choices) {}

    /** guestToken chỉ có ở lần tạo lượt của khách và không lấy lại được (server chỉ lưu hash). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Started(Long attemptId, String guestToken, String title, Instant startedAt, Instant deadlineAt, int timeLimitSec,
                          long remainingSec, JsonNode draftAnswers, List<QuestionOut> questions) {}

    public record Progress(long remainingSec) {}

    /** correct là tổng điểm câu (có thể lẻ do chấm từng phần). */
    public record SkillResult(BigDecimal correct, int total, BigDecimal percent, BigDecimal band) {}

    public record CourseRef(Long courseId, String slug, String title) {}

    public record Result(Long attemptId, Instant submittedAt, Map<String, SkillResult> skills, BigDecimal overallBand,
                         CourseRef recommendedCourse) {}

    /** Khách: requiresLogin = true và không có result (phải đăng ký/đăng nhập rồi claim mới xem được). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Submitted(boolean submitted, boolean requiresLogin, Result result) {}

    /** result null = chưa làm bài, giao diện mời làm. */
    public record MyPlacement(Result result) {}

    /** reason: null khi có khóa; NO_PLACEMENT chưa làm bài; NO_MATCHING_COURSE không khóa nào hợp dải band. */
    public record Recommendation(CourseRef course, String reason, BigDecimal band) {}

    // ---- CMS ----

    public record ConfigView(Integer maxRetakes) {}

    public record ConfigUpdate(@Min(0) @Max(100) Integer maxRetakes) {}

    public record BandRuleInput(
            @NotNull @Pattern(regexp = "LISTENING|READING|GRAMMAR", message = "Chỉ nhận LISTENING, READING hoặc GRAMMAR") String skill,
            @NotNull @Min(0) @Max(100) Integer minPercent,
            @NotNull @DecimalMin("0") @DecimalMax("9") BigDecimal band) {}

    public record ReplaceBandRules(@NotNull @Size(max = 60) List<@Valid @NotNull BandRuleInput> rules) {}

    public record BandRuleView(String skill, int minPercent, BigDecimal band) {}
}

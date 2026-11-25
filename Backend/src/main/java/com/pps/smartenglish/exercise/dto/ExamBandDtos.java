package com.pps.smartenglish.exercise.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** FR-EXAM-03, D-12: bảng quy đổi số câu đúng -> band của đề thi khóa và band tính từ một lượt đã nộp. */
public final class ExamBandDtos {

    private ExamBandDtos() {}

    public record RuleInput(
            @NotNull @Pattern(regexp = "LISTENING|READING|GRAMMAR", message = "Chỉ nhận LISTENING, READING hoặc GRAMMAR") String skill,
            @NotNull @Min(0) @Max(1000) Integer minCorrect,
            @NotNull @DecimalMin("0") @DecimalMax("9999") @Digits(integer = 4, fraction = 1, message = "Band chỉ nhận tối đa 1 chữ số thập phân") BigDecimal band) {}

    public record ReplaceRules(@NotNull @Size(max = 200) List<@Valid @NotNull RuleInput> rules) {}

    public record RuleView(String skill, int minCorrect, BigDecimal band) {}

    /** correct: tổng điểm câu (có thể lẻ do chấm từng phần); total: số câu của kỹ năng trong đề. */
    public record SkillBand(BigDecimal correct, int total, BigDecimal band) {}

    /**
     * skills: chỉ kỹ năng có bảng quy đổi. overall: trung bình các band đó, làm tròn 0,5 kiểu IELTS (còn chờ chốt cho chương
     * trình khác IELTS, D-105). gradingPending: còn câu AI chấm chưa xong, band có thể tăng.
     */
    public record AttemptBands(Map<String, SkillBand> skills, BigDecimal overall, boolean gradingPending) {}
}

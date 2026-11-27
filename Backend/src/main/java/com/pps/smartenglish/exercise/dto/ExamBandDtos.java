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

/** FR-EXAM-03, D-12, D-167: bảng quy đổi % điểm của kỹ năng -> band của đề thi khóa và band tính từ một lượt đã nộp. */
public final class ExamBandDtos {

    private ExamBandDtos() {}

    public record RuleInput(
            @NotNull @Pattern(regexp = "LISTENING|READING|GRAMMAR|WRITING|SPEAKING", message = "Chỉ nhận LISTENING, READING, GRAMMAR, WRITING hoặc SPEAKING") String skill,
            @NotNull @Min(0) @Max(100) Integer minPercent,
            @NotNull @DecimalMin("0") @DecimalMax("9999") @Digits(integer = 4, fraction = 1, message = "Band chỉ nhận tối đa 1 chữ số thập phân") BigDecimal band) {}

    public record ReplaceRules(@NotNull @Size(max = 200) List<@Valid @NotNull RuleInput> rules) {}

    public record RuleView(String skill, int minPercent, BigDecimal band) {}

    /**
     * correct: tổng điểm câu (có thể lẻ do chấm từng phần); total: số câu của kỹ năng trong đề; percent: correct / total x 100.
     * band null khi kỹ năng chưa có bảng quy đổi hoặc còn câu của kỹ năng đang chấm (D-167).
     */
    public record SkillBand(BigDecimal correct, int total, BigDecimal percent, BigDecimal band) {}

    /**
     * skills: mọi kỹ năng có câu trong đề. overall: trung bình các band kỹ năng có bảng, làm tròn 0,5 kiểu IELTS (còn chờ chốt cho
     * chương trình khác IELTS, D-105); null khi chưa có band nào hoặc còn câu đang chấm. gradingPending: còn câu AI chấm chưa xong.
     */
    public record AttemptBands(Map<String, SkillBand> skills, BigDecimal overall, boolean gradingPending) {}
}

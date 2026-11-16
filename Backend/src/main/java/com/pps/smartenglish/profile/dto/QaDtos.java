package com.pps.smartenglish.profile.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pps.smartenglish.profile.domain.SelfLevel;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;

/** Bộ Q&A gợi ý khóa (D-55, FR-GOAL-01). Mã enum bằng tiếng Anh; nhãn tiếng Việt do web hiển thị. */
public final class QaDtos {

    private QaDtos() {}

    public enum Goal { IELTS_CERT, STUDY_ABROAD, WORK, SCHOOL, UNSURE }

    /** LT3 < 3h, H3_5 3-5h, H5_10 5-10h, GT10 > 10h mỗi tuần. */
    public enum WeeklyHours { LT3, H3_5, H5_10, GT10 }

    public enum Skill { LISTENING, SPEAKING, READING, WRITING }

    /** skills có thể rỗng (không chọn kỹ năng nào); các trường còn lại bắt buộc. */
    public record QaRequest(
            @NotNull(message = "Trình độ hiện tại là bắt buộc") SelfLevel selfLevel,
            @NotNull(message = "Mục tiêu là bắt buộc") @Size(min = 1, max = 2, message = "Chọn từ 1 đến 2 mục tiêu") List<@NotNull Goal> goals,
            @NotNull(message = "Band mục tiêu là bắt buộc")
            @DecimalMin(value = "4.0", message = "Band mục tiêu từ 4.0 đến 8.0")
            @DecimalMax(value = "8.0", message = "Band mục tiêu từ 4.0 đến 8.0") BigDecimal targetBand,
            @NotNull(message = "Thời gian học mỗi tuần là bắt buộc") WeeklyHours weeklyHours,
            @NotNull(message = "Kỹ năng là bắt buộc") @Size(max = 4, message = "Tối đa 4 kỹ năng") List<@NotNull Skill> skills) {

        @JsonIgnore
        @AssertTrue(message = "Mục tiêu không được trùng nhau")
        public boolean isGoalsDistinct() {
            return goals == null || new HashSet<>(goals).size() == goals.size();
        }

        @JsonIgnore
        @AssertTrue(message = "Kỹ năng không được trùng nhau")
        public boolean isSkillsDistinct() {
            return skills == null || new HashSet<>(skills).size() == skills.size();
        }

        @JsonIgnore
        @AssertTrue(message = "Band mục tiêu theo bước 0.5")
        public boolean isTargetBandStepValid() {
            return targetBand == null || targetBand.multiply(BigDecimal.TWO).stripTrailingZeros().scale() <= 0;
        }
    }

    /** Dạng đã lưu và trả về; targetBand luôn một chữ số thập phân. */
    public record QaAnswers(SelfLevel selfLevel, List<Goal> goals, BigDecimal targetBand, WeeklyHours weeklyHours,
                            List<Skill> skills) {}

    /** Phản hồi của GET/PUT /api/me/qa: các câu trả lời kèm thời điểm lưu. */
    public record QaView(SelfLevel selfLevel, List<Goal> goals, BigDecimal targetBand, WeeklyHours weeklyHours,
                         List<Skill> skills, Instant updatedAt) {

        public static QaView of(QaAnswers a, Instant updatedAt) {
            return new QaView(a.selfLevel(), a.goals(), a.targetBand(), a.weeklyHours(), a.skills(), updatedAt);
        }
    }
}

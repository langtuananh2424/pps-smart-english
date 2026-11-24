package com.pps.smartenglish.classroom.dto;

import com.pps.smartenglish.exercise.listening.ListeningDtos.RuleInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** FR-ASG-01..04. */
public final class AssignmentDtos {

    private AssignmentDtos() {}

    /**
     * Giao một bộ câu hỏi (bankId) hoặc một câu Viết/Nói chấm AI (questionId), đúng một trong hai (D-130).
     * studentIds trống hoặc vắng = cả lớp.
     * forcedListening (FR-EX-10, D-136): ghi đè luật Nghe ép cho bài giao; chỉ với bộ ở chế độ LOCKED, trường vắng giữ luật của bộ.
     */
    public record CreateAssignment(
            Long bankId,
            Long questionId,
            @NotNull(message = "Hạn nộp là bắt buộc") Instant dueAt,
            @Size(max = 200, message = "Tối đa 200 học viên") List<Long> studentIds,
            @Valid RuleInput forcedListening) {}

    /** questionId, questionType (WRITING, SPEAKING): bài giao câu Viết/Nói, null khi giao cả bộ. myStatus chỉ điền khi người xem là học viên. */
    public record AssignmentView(Long id, Long classId, Long bankId, String bankTitle, Long questionId, String questionType, Instant dueAt,
                                 String status, boolean wholeClass, String myStatus) {}

    /**
     * status: NOT_SUBMITTED, SUBMITTED, LATE (nộp sau hạn), OVERDUE (quá hạn chưa nộp). finalScore = điểm giáo viên sửa mới nhất, không có thì điểm AI.
     * Bài giao Nghe ép (D-143): status, attemptId, submittedAt theo lượt nộp ĐẦU TIÊN (trễ hạn theo lượt đó), không có điểm (D-142); forced
     * có lượt mới nhất (số câu đúng, đạt chưa), số lần làm, đã có lượt nào đạt.
     * attemptId: id lượt làm bài; với bài giao câu Viết là id phiên viết, câu Nói là id lượt nói. Thang điểm bài Viết 100, bài Nói 9 band.
     */
    public record ProgressRow(Long userId, String displayName, String status, Long attemptId, Instant submittedAt, BigDecimal aiScore,
                              Integer maxScore, BigDecimal finalScore, String teacherComment,
                              com.pps.smartenglish.exercise.listening.ListeningDtos.ForcedProgress forced) {}

    public record GradeRequest(
            @DecimalMin(value = "0", message = "Điểm không âm") BigDecimal score,
            @Size(max = 4000, message = "Nhận xét tối đa 4000 ký tự") String comment) {}

    public record GradeView(Long id, Long attemptId, BigDecimal score, String comment, Long gradedBy, Instant gradedAt) {}

    /** Kết quả của chính học viên cho một bài giao; điểm AI hiện ngay, điểm giáo viên sửa thay thế khi có (D-113). */
    public record MyResult(Long assignmentId, String status, Long attemptId, Instant submittedAt, BigDecimal aiScore, Integer maxScore,
                           BigDecimal finalScore, String teacherComment) {}

    /**
     * D-141: bài nộp câu Viết/Nói cho giáo viên. Đúng một trong hai: writing (phiên viết như học viên tự xem) hoặc speaking (lượt nói,
     * audioUrl ký ngắn hạn, kết quả gồm chép lời). audioDeleted: ghi âm đã xóa theo D-43, vẫn có chép lời và kết quả.
     */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record AiSubmissionDetail(Long assignmentId, Long userId, String questionType, Instant submittedAt,
                                     com.pps.smartenglish.ai.dto.WritingDtos.SessionView writing,
                                     com.pps.smartenglish.ai.speaking.SpeakingDtos.SubmissionView speaking, Boolean audioDeleted) {}
}

package com.pps.smartenglish.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Request/response của luồng viết. Giới hạn 5000 ký tự là trần chi phí token (FR-AI-01). */
public final class WritingDtos {

    public static final int MAX_CHARS = 5000;

    private WritingDtos() {}

    /** assignmentId: làm trong ngữ cảnh bài giáo viên giao (FR-ASG-02, D-130). */
    /** attemptId: mở từ trong lượt làm bài đang mở của bộ có câu này, điểm AI sẽ cộng vào lượt (D-145). */
    public record CreateSessionRequest(@NotNull(message = "Bắt buộc") Long questionId, Long assignmentId, Long attemptId) {}

    public record AnswerRequest(
            @NotBlank(message = "Không được để trống") @Size(max = MAX_CHARS, message = "Tối đa 5000 ký tự") String text) {}

    public record SubmitRequest(
            @NotBlank(message = "Không được để trống") @Size(max = MAX_CHARS, message = "Tối đa 5000 ký tự") String finalText) {}

    public record RootQuestion(Long id, String prompt, JsonNode payload) {}

    public record SessionCreated(Long sessionId, RootQuestion question) {}

    /** jobId null khi đủ số câu hỏi đào sâu: session chuyển READY ngay, không gọi AI. */
    public record AnswerAccepted(Long jobId, String sessionStatus) {}

    public record DraftAccepted(Long jobId) {}

    public record SubmitAccepted(Long jobId, String status, int quotaRemaining) {}

    /** role: CAM (câu hỏi của Cam) hoặc STUDENT. */
    public record TurnView(String role, String text) {}

    /** pendingJobId: job Scaffolding đang chạy; grade: kết quả chấm, chỉ có khi job WRITING_GRADE đã DONE. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SessionView(Long sessionId, String status, RootQuestion question, List<TurnView> turns, Long pendingJobId,
                              String draft, String finalText, Long gradeJobId, JsonNode grade, Instant createdAt) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SessionSummary(Long sessionId, Long questionId, String status, Integer scorePercent, Instant createdAt, Long assignmentId) {}
}

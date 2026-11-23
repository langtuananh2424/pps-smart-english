package com.pps.smartenglish.ai.speaking;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class SpeakingDtos {

    private SpeakingDtos() {}

    /** assignmentId: làm trong ngữ cảnh bài giáo viên giao (FR-ASG-02, D-130). */
    public record CreateRequest(@NotNull(message = "Bắt buộc") Long questionId, Long assignmentId) {}

    /** Client POST multipart tới url: các fields theo đúng thứ tự, rồi field `file` cuối cùng. */
    public record UploadPermit(long submissionId, String url, Map<String, String> fields, long maxBytes, String contentType,
                               int maxSeconds, Instant expiresAt) {}

    public record CompleteResponse(long submissionId, String status, Long jobId, Integer quotaRemaining) {}

    /** audioUrl: URL ký ngắn hạn, null khi chưa có hoặc đã xóa audio. */
    public record SubmissionView(long id, long questionId, String status, JsonNode result, String audioUrl, BigDecimal durationSec,
                                 Instant createdAt) {}

    /** Đề nói cho màn Nói (cueCard rỗng nếu không phải part 2). */
    public record QuestionView(long id, int part, String prompt, List<String> cueCard, int maxSeconds) {}
}

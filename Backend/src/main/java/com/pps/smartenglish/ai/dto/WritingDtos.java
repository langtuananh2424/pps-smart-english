package com.pps.smartenglish.ai.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request/response của luồng viết. Giới hạn 5000 ký tự là trần chi phí token (FR-AI-01). */
public final class WritingDtos {

    public static final int MAX_CHARS = 5000;

    private WritingDtos() {}

    public record CreateSessionRequest(@NotNull(message = "Bắt buộc") Long questionId) {}

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
}

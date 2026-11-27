package com.pps.smartenglish.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** D-159 (FR-EX-04): xem video khóa tua và quiz sau video của nhóm. */
public final class VideoQuizDtos {

    private VideoQuizDtos() {}

    /** position tính bằng giây; thời lượng lấy từ máy chủ (mvhd của file hoặc CMS nhập cho YouTube), không nhận từ client. */
    public record ProgressRequest(@NotNull Long groupId, @NotNull @DecimalMin("0") @DecimalMax("86400") Double position) {}

    public record ProgressResult(Long groupId, double watchedSec, Double durationSec, boolean watched) {}

    public record QuizRequest(@NotNull @Size(min = 1, max = 100) List<@Valid QuizAnswer> answers) {}

    public record QuizAnswer(@NotNull Long questionId, JsonNode answer) {}

    /** Đáp án đúng và giải thích chỉ có khi nhóm đã chốt. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QuestionOutcome(Long questionId, boolean correct, JsonNode correctAnswer, String explanation) {}

    /** answers: câu trả lời của lần nộp cuối (để dựng lại màn khi tải lại trang); vắng khi chưa nộp lần nào. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GroupState(Long groupId, double watchedSec, Double durationSec, boolean watched, int submits, int maxSubmits,
                             boolean finalized, boolean passed, List<QuizAnswer> answers, List<QuestionOutcome> results) {}

    /** attemptNo: lượt thứ mấy của bộ; passedAttempts/requiredAttempts: số lượt đã đạt và cần đạt để bộ hoàn thành. */
    public record State(int attemptNo, long passedAttempts, int requiredAttempts, List<GroupState> groups) {}
}

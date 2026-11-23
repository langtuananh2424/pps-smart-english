package com.pps.smartenglish.exercise.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Câu không có trong danh sách (hoặc answer null) được tính là bỏ trống. */
public record SubmitRequest(
        @NotNull(message = "Bắt buộc") @Size(max = 500, message = "Tối đa 500 câu") List<@Valid @NotNull AnswerInput> answers) {

    /** timeSpentMs (FR-EX-08): tùy chọn; ngoài 0..max cấu hình thì máy chủ bỏ (lưu null), không từ chối lần nộp. */
    public record AnswerInput(@NotNull(message = "Bắt buộc") Long questionId, JsonNode answer, Long timeSpentMs) {}
}

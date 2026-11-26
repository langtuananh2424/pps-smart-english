package com.pps.smartenglish.exercise.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** D-150: thử lại kèm gợi ý. Không có đáp án đúng hay giải thích ở đây; học viên xem ở màn kết quả. */
public final class CheckDtos {

    private CheckDtos() {}

    /** Câu trả lời cùng định dạng với một phần tử answers của lần nộp bài. */
    public record CheckRequest(@NotNull(message = "Bắt buộc") JsonNode answer) {}

    /** correct: lần kiểm tra mới nhất đúng. checksLeft: số lần kiểm tra sai còn được phép; câu khóa khi đúng hoặc checksLeft = 0. */
    public record CheckResult(Long questionId, boolean correct, int checksLeft) {}

    /** Có trong tiến độ của lượt áp dụng thử lại; checks chỉ gồm câu đã được kiểm tra. */
    public record RetryState(int maxRetries, List<CheckResult> checks) {}
}

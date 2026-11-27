package com.pps.smartenglish.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** D-150, D-163: thử lại kèm gợi ý. Đáp án đúng và giải thích chỉ có khi câu hết lượt thử mà vẫn sai; còn lại để màn kết quả. */
public final class CheckDtos {

    private CheckDtos() {}

    /** Câu trả lời cùng định dạng với một phần tử answers của lần nộp bài. */
    public record CheckRequest(@NotNull(message = "Bắt buộc") JsonNode answer) {}

    /** correct: lần kiểm tra mới nhất đúng. checksLeft: số lần kiểm tra sai còn được phép; câu khóa khi đúng hoặc checksLeft = 0.
     * wrongCount: số lần kiểm tra sai của câu. correctAnswer, explanation, choiceNotes: chỉ khi khóa sai (hết lượt, chưa đúng). */
    public record CheckResult(Long questionId, boolean correct, int checksLeft, int wrongCount,
                              @JsonInclude(JsonInclude.Include.NON_NULL) JsonNode correctAnswer,
                              @JsonInclude(JsonInclude.Include.NON_NULL) String explanation,
                              @JsonInclude(JsonInclude.Include.NON_NULL) List<ChoiceNote> choiceNotes) {}

    /** Có trong tiến độ của lượt áp dụng thử lại; checks chỉ gồm câu đã được kiểm tra. */
    public record RetryState(int maxRetries, int hintAfterWrong, List<CheckResult> checks) {}
}

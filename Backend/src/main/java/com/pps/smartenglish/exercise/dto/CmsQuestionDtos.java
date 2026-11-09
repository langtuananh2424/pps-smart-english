package com.pps.smartenglish.exercise.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * DTO CMS bộ câu hỏi và câu hỏi (FR-ADM-02). Khác DTO của học viên (BankResponse): ở đây CÓ answerKey và isCorrect, nên chỉ
 * được trả cho ADMIN/EDITOR. PATCH: trường null = vắng mặt = giữ nguyên; payload/answerKey gửi JSON null nghĩa là xóa.
 */
public final class CmsQuestionDtos {

    private static final String NOT_BLANK = ".*\\S.*";

    private CmsQuestionDtos() {}

    // ---- yêu cầu ----

    public record CreateBank(@NotBlank @Size(max = 200) String title, Long lessonId, Boolean isTrial) {}

    public record PatchBank(@Size(max = 200) @Pattern(regexp = NOT_BLANK, message = "Không được để trống") String title,
                            Long lessonId, Boolean isTrial) {}

    public record ChoiceInput(@NotBlank @Size(max = 10) String label, @NotBlank String content, Boolean isCorrect, String explanation) {}

    public record CreateQuestion(
            @NotBlank String type,
            @NotBlank @Size(max = 5000) String prompt,
            JsonNode payload,
            JsonNode answerKey,
            @Size(max = 10) List<@NotNull @Valid ChoiceInput> choices,
            @Size(max = 5000) String explanation,
            @Min(0) Integer sortOrder,
            @Size(max = 8) List<String> grammarElementCodes) {}

    public record PatchQuestion(
            String type,
            @Size(max = 5000) @Pattern(regexp = NOT_BLANK, message = "Không được để trống") String prompt,
            JsonNode payload,
            JsonNode answerKey,
            @Size(max = 10) List<@NotNull @Valid ChoiceInput> choices,
            @Size(max = 5000) String explanation,
            @Min(0) Integer sortOrder,
            @Size(max = 8) List<String> grammarElementCodes) {}

    // ---- phản hồi ----

    public record BankView(Long id, Long topicId, Long lessonId, String title, boolean isTrial, String status, long questionCount) {}

    public record ChoiceView(Long id, String label, String content, boolean isCorrect, String explanation) {}

    /** answerVersion tăng mỗi lần đáp án thật sự đổi; regradeRequired: có bài nộp, dạng chấm lại được, chưa có run ở phiên bản này. */
    public record QuestionDetail(Long id, Long bankId, String type, String prompt, JsonNode payload, JsonNode answerKey,
                                 List<ChoiceView> choices, String explanation, int sortOrder, List<String> grammarElementCodes,
                                 int answerVersion, boolean regradeRequired) {}

    public record BankDetail(BankView bank, List<QuestionDetail> questions) {}

    public record GrammarElementView(Long id, String code, String name, String colorHex, int sortOrder) {}
}

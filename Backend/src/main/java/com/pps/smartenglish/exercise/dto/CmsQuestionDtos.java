package com.pps.smartenglish.exercise.dto;

import com.pps.smartenglish.exercise.dto.QuestionMediaDtos.QuestionMediaView;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.pps.smartenglish.exercise.dto.QuestionGroupDtos.GroupView;
import java.util.List;

/**
 * DTO CMS bộ câu hỏi và câu hỏi (FR-ADM-02). Khác DTO của học viên (BankResponse): ở đây CÓ answerKey và isCorrect, nên chỉ
 * được trả cho ADMIN/EDITOR. PATCH: trường null = vắng mặt = giữ nguyên; payload/answerKey gửi JSON null nghĩa là xóa.
 */
public final class CmsQuestionDtos {

    private static final String NOT_BLANK = ".*\\S.*";
    private static final String SKILL = "LISTENING|READING|GRAMMAR";
    private static final String PLAYBACK = "FREE|EXAM|LOCKED";

    private CmsQuestionDtos() {}

    // ---- yêu cầu ----

    public record CreateBank(@NotBlank @Size(max = 200) String title, Long lessonId, Boolean isTrial,
                             @Min(value = 1, message = "Phải từ 1 giây trở lên") Integer timeLimitSec,
                             @Pattern(regexp = "PRACTICE|EXAM", message = "Chế độ phải là PRACTICE hoặc EXAM") String mode,
                             @Pattern(regexp = PLAYBACK, message = "Chế độ phát phải là FREE, EXAM hoặc LOCKED") String playbackMode,
                             @Min(value = 1, message = "Phải từ 1 lần trở lên") Integer playLimit) {}

    public record PatchBank(@Size(max = 200) @Pattern(regexp = NOT_BLANK, message = "Không được để trống") String title,
                            Long lessonId, Boolean isTrial,
                            @Min(value = 1, message = "Phải từ 1 giây trở lên") Integer timeLimitSec,
                            @Pattern(regexp = "PRACTICE|EXAM", message = "Chế độ phải là PRACTICE hoặc EXAM") String mode,
                            @Pattern(regexp = PLAYBACK, message = "Chế độ phát phải là FREE, EXAM hoặc LOCKED") String playbackMode,
                            @Min(value = 1, message = "Phải từ 1 lần trở lên") Integer playLimit) {}

    public record CreatePlacementBank(@NotBlank @Size(max = 200) String title,
                                      @NotNull @Min(value = 1, message = "Phải từ 1 giây trở lên") Integer timeLimitSec) {}

    /** content hoặc contentHtml phải có ít nhất một (service kiểm); có contentHtml thì content do server suy ra từ HTML. */
    public record ChoiceInput(@NotBlank @Size(max = 10) String label, String content, @Size(max = 5000) String contentHtml,
                              Boolean isCorrect, String explanation) {}

    public record CreateQuestion(
            @NotBlank String type,
            @Size(max = 5000) String prompt,
            @Size(max = 20000) String promptHtml,
            Long groupId,
            JsonNode payload,
            JsonNode answerKey,
            @Size(max = 10) List<@NotNull @Valid ChoiceInput> choices,
            @Size(max = 5000) String explanation,
            @Min(0) Integer sortOrder,
            @Size(max = 8) List<String> grammarElementCodes,
            @Pattern(regexp = SKILL, message = "Chỉ nhận LISTENING, READING hoặc GRAMMAR") String skill,
            @Size(max = 300) String instruction,
            @Size(max = 5000) String transcriptExcerpt) {}

    public record PatchQuestion(
            String type,
            @Size(max = 5000) @Pattern(regexp = NOT_BLANK, message = "Không được để trống") String prompt,
            @Size(max = 20000) String promptHtml,
            /** Vắng mặt = giữ; JSON null = bỏ khỏi nhóm; số = chuyển nhóm (cùng quy ước với payload/answerKey). */
            JsonNode groupId,
            JsonNode payload,
            JsonNode answerKey,
            @Size(max = 10) List<@NotNull @Valid ChoiceInput> choices,
            @Size(max = 5000) String explanation,
            @Min(0) Integer sortOrder,
            @Size(max = 8) List<String> grammarElementCodes,
            @Pattern(regexp = SKILL, message = "Chỉ nhận LISTENING, READING hoặc GRAMMAR") String skill,
            @Size(max = 300) String instruction,
            /** Vắng mặt = giữ; chuỗi rỗng = xóa (về dùng script của nhóm). */
            @Size(max = 5000) String transcriptExcerpt) {}

    // ---- phản hồi ----

    public record BankView(Long id, Long topicId, Long lessonId, String title, boolean isTrial, String status, long questionCount,
                           Integer timeLimitSec, String mode, String purpose, String playbackMode, Integer playLimit) {}

    public record ChoiceView(Long id, String label, String content, String contentHtml, boolean isCorrect, String explanation) {}

    /** answerVersion tăng mỗi lần đáp án thật sự đổi; regradeRequired: có bài nộp, dạng chấm lại được, chưa có run ở phiên bản này. */
    public record QuestionDetail(Long id, Long bankId, String type, String prompt, JsonNode payload, JsonNode answerKey,
                                 List<ChoiceView> choices, String explanation, int sortOrder, List<String> grammarElementCodes,
                                 int answerVersion, boolean regradeRequired, String skill, String instruction,
                                 List<QuestionMediaView> media, Long groupId, String promptHtml, String transcriptExcerpt) {}

    public record BankDetail(BankView bank, List<QuestionDetail> questions, List<GroupView> groups) {}

    public record GrammarElementView(Long id, String code, String name, String colorHex, int sortOrder) {}
}

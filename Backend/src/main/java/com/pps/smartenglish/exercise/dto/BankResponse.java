package com.pps.smartenglish.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.dto.QuestionMediaDtos.QuestionMediaView;
import java.util.List;

/**
 * Không có trường nào mang đáp án: không answerKey, is_correct hay explanation, cũng không script (FR-EX-10).
 * playbackMode/playLimit (D-135): chế độ phát audio và số lần nghe mỗi part theo bộ (null = không giới hạn); bài giao có thể
 * ghi đè, số chính xác của lượt đang làm nằm ở phản hồi cấp link nghe.
 */
public record BankResponse(Long id, String title, Long lessonId, boolean isTrial, String mode,
                         Integer timeLimitSec, List<QuestionView> questions, List<GroupView> groups, String playbackMode,
                         Integer playLimit) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QuestionView(Long id, String type, String instruction, String prompt, JsonNode payload, List<ChoiceView> choices,
                        List<HintView> hints, List<QuestionMediaView> media, Long groupId, String promptHtml) {}

    public record ChoiceView(Long id, String label, String content, String contentHtml) {}

    /** D-133: bài đọc dùng chung; passageHtml đã làm sạch và có src ký sẵn cho ảnh. */
    public record GroupView(Long id, String title, String instruction, String passageHtml, int sortOrder, boolean hasAudio,
                            Integer audioDurationMs) {}

    /** FR-AI-06: gợi ý soạn sẵn; chỉ có ở bộ PRACTICE, bộ EXAM luôn là mảng rỗng. */
    public record HintView(Long id, String content, String detail) {}
}

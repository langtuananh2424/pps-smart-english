package com.pps.smartenglish.exercise.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** D-133 (FR-ADM-02): nhóm câu hỏi dùng chung bài đọc. Chỉ CMS ghi; học viên đọc qua BankResponse.GroupView. */
public final class QuestionGroupDtos {

    private QuestionGroupDtos() {}

    /** audioAssetId: audio READY của part (D-135); transcriptHtml: script của part, chỉ dùng cho Nghe ép (FR-EX-10). */
    public record CreateGroup(@Size(max = 200) String title, @Size(max = 300) String instruction,
                              @Size(max = 100000) String passageHtml, @Min(0) Integer sortOrder, Long audioAssetId,
                              @Size(max = 100000) String transcriptHtml) {}

    /** null = giữ nguyên; chuỗi rỗng/chỉ khoảng trắng = xóa trường (title, instruction, passageHtml). */
    public record PatchGroup(@Size(max = 200) String title, @Size(max = 300) String instruction,
                             @Size(max = 100000) String passageHtml, @Min(0) Integer sortOrder,
                             /** Vắng mặt = giữ; JSON null = gỡ audio; số = đổi audio (cùng quy ước với groupId của câu hỏi). */
                             JsonNode audioAssetId, @Size(max = 100000) String transcriptHtml) {}

    public record GroupView(Long id, Long bankId, int sortOrder, String title, String instruction, String passageHtml,
                            Long audioAssetId, Integer audioDurationMs, String transcriptHtml) {}
}

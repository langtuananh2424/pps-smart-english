package com.pps.smartenglish.exercise.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** D-133 (FR-ADM-02): nhóm câu hỏi dùng chung bài đọc. Chỉ CMS ghi; học viên đọc qua BankResponse.GroupView. */
public final class QuestionGroupDtos {

    private QuestionGroupDtos() {}

    public record CreateGroup(@Size(max = 200) String title, @Size(max = 300) String instruction,
                              @Size(max = 100000) String passageHtml, @Min(0) Integer sortOrder) {}

    /** null = giữ nguyên; chuỗi rỗng/chỉ khoảng trắng = xóa trường (title, instruction, passageHtml). */
    public record PatchGroup(@Size(max = 200) String title, @Size(max = 300) String instruction,
                             @Size(max = 100000) String passageHtml, @Min(0) Integer sortOrder) {}

    public record GroupView(Long id, Long bankId, int sortOrder, String title, String instruction, String passageHtml) {}
}

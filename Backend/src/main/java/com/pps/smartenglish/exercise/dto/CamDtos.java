package com.pps.smartenglish.exercise.dto;

/**
 * FR-AI-06, FR-AI-07: nội dung Cam soạn sẵn. Chuỗi kiểm tra ở service (PlainText) vì phải đo độ dài sau khi làm sạch.
 * PATCH: trường null = giữ nguyên; detail rỗng = xóa popup.
 */
public final class CamDtos {

    private CamDtos() {}

    public record HintInput(String content, String detail, Integer sortOrder) {}

    public record HintView(Long id, Long questionId, int sortOrder, String content, String detail) {}

    public record FaqInput(String question, String answer, Integer sortOrder) {}

    public record FaqView(Long id, Long bankId, int sortOrder, String question, String answer) {}

    /** Học viên: câu hỏi - đáp, đã theo thứ tự. */
    public record FaqItem(Long id, String question, String answer) {}
}

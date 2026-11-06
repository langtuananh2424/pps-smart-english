package com.pps.smartenglish.ai.dto;

import java.util.List;

/**
 * Output cấu trúc của writing-rubric-ielts.md. Chỉ 4 điểm tiêu chí; điểm tổng (scorePercent) do code tính.
 * Model chỉ liệt kê lỗi trong `marks`; markedAnswer do code dựng từ bài gốc vì model chép lại bài không đáng tin từng ký tự.
 */
public record WritingGrade(int taskResponse, int coherence, int lexical, int grammar, List<Mark> marks,
                           String correctedAnswer, Connectives connectives, List<GrammarElement> grammarElements,
                           String gateNote, String feedback) {

    /** code: ok, sp1, sp2, gr1, gr2, wd1, wd2, pu1, pu2; quote: đoạn nguyên văn trong bài gốc. */
    public record Mark(String code, String quote) {}

    public record Connectives(int total, int types, int correct, List<String> misused, String note) {}

    /** status: USED_WELL, USED_WRONG hoặc SUGGESTED. */
    public record GrammarElement(String code, String status, String quote, String upgrade) {}
}

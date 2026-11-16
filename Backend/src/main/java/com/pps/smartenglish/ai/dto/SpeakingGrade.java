package com.pps.smartenglish.ai.dto;

import java.util.List;

/** Output của speaking-rubric-ielts.md: chỉ hai tiêu chí cần hiểu ngôn ngữ; phát âm và trôi chảy do code tính. */
public record SpeakingGrade(double lexicalBand, double grammarBand, String feedback, List<Suggestion> suggestions) {

    /** original: đoạn nguyên văn trong bản chép lời; improved: bản sửa; reason: giải thích ngắn bằng tiếng Việt. */
    public record Suggestion(String original, String improved, String reason) {}
}

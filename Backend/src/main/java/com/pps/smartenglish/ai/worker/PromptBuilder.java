package com.pps.smartenglish.ai.worker;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/**
 * Dựng user prompt từ snapshot trong ai_jobs.input. Dữ liệu của học viên luôn nằm giữa hai dấu ===
 * để system prompt phân biệt dữ liệu với chỉ dẫn.
 */
@Component
class PromptBuilder {

    String followup(JsonNode in) {
        return header(in) + "Các lượt hỏi đáp:\n===\n" + transcript(in.path("turns")) + "\n===\n";
    }

    String draft(JsonNode in) {
        return header(in) + "Các câu trả lời của học viên:\n===\n" + transcript(in.path("turns")) + "\n===\n";
    }

    String grading(JsonNode in) {
        String required = in.hasNonNull("requiredWords") ? in.path("requiredWords").asText() : "không ghi";
        return "Khóa học: " + in.path("courseTitle").asText() + "\n"
                + "Đề bài: " + in.path("task").asText() + "\n"
                + "Số từ yêu cầu: " + required + "\n"
                + "Số từ máy đã đếm: " + in.path("wordCount").asInt() + "\n"
                + "Số từ chép từ đề: " + in.path("copiedWords").asInt() + "\n"
                + "Bài viết:\n===\n" + in.path("text").asText() + "\n===\n";
    }

    /** Các mục cần chấm là mảng JSON giữa ===. */
    String rewrite(JsonNode items) {
        return "Các mục cần chấm:\n===\n" + items + "\n===\n";
    }

    private static String header(JsonNode in) {
        return "Khóa học: " + in.path("courseTitle").asText() + "\nĐề bài: " + in.path("task").asText() + "\n";
    }

    private static String transcript(JsonNode turns) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode t : turns) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("ASSISTANT".equals(t.path("role").asText()) ? "Hỏi: " : "Đáp: ").append(t.path("text").asText());
        }
        return sb.toString();
    }
}

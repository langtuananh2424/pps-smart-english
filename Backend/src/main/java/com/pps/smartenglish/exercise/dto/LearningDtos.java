package com.pps.smartenglish.exercise.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** FR-EX-08, FR-EX-09, D-137: số liệu kết quả cuối bài và hồ sơ năng lực, tính khi truy vấn từ student_answers. */
public final class LearningDtos {

    private LearningDtos() {}

    /**
     * Một dòng của một chiều thống kê. answered/correct chỉ đếm câu đã có đúng/sai (accuracy = correct/answered, %);
     * câu dạng AI chưa có đúng/sai (ungraded) tách riêng: aiGraded đã có điểm Cam (aiAverageScore, % 0 đến 100, D-167: Viết là
     * scorePercent, Nói là band / bandMax x 100), phần còn lại đang chờ chấm. Không trộn vào accuracy.
     */
    public record Metric(String key, String label, long answered, long correct, BigDecimal credit, Integer accuracy,
                         long ungraded, long aiGraded, BigDecimal aiAverageScore) {}

    /** Số lần sai tích lũy của học viên với câu này, chỉ tính các lần cùng phiên bản đáp án hiện tại. */
    public record WrongQuestion(long questionId, long wrongCount) {}

    /** Phân rã của đúng một lượt làm. */
    public record Breakdown(long correct, long wrong, long ungraded, List<Metric> bySkill, List<Metric> byType,
                            List<Metric> byGrammar, List<WrongQuestion> wrongQuestions) {}

    /** Một tuần (thứ Hai bắt đầu, giờ Việt Nam); tuần không học vẫn có mặt với số 0. */
    public record WeekPoint(LocalDate weekStart, long answered, long correct, Integer accuracy) {}

    /** Gợi ý cần cải thiện theo luật; dimension là SKILL hoặc GRAMMAR. */
    public record Improvement(String dimension, String key, String label, int accuracy, long answered) {}

    public record Profile(long answered, long correct, BigDecimal credit, Integer accuracy, List<Metric> bySkill,
                          List<Metric> byType, List<Metric> byGrammar, List<Metric> byTopic, List<WeekPoint> weeklyTrend,
                          List<Improvement> improvements) {}
}

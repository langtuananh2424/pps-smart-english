package com.pps.smartenglish.classroom.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** FR-CLS-05, FR-CLS-06, FR-CLS-07. */
public final class ProgressDtos {

    private ProgressDtos() {}

    public record Thresholds(int overdueAssignments, int absences, int unitsBehind, int minAvgPercent) {}

    public record UpdateThresholds(
            @NotNull(message = "Bắt buộc") @Min(value = 1, message = "Tối thiểu 1") @Max(value = 1000, message = "Quá lớn") Integer overdueAssignments,
            @NotNull(message = "Bắt buộc") @Min(value = 1, message = "Tối thiểu 1") @Max(value = 1000, message = "Quá lớn") Integer absences,
            @NotNull(message = "Bắt buộc") @Min(value = 1, message = "Tối thiểu 1") @Max(value = 1000, message = "Quá lớn") Integer unitsBehind,
            @NotNull(message = "Bắt buộc") @Min(value = 0, message = "Từ 0") @Max(value = 100, message = "Tối đa 100") Integer minAvgPercent) {}

    public record AssignmentCounts(int submitted, int late, int overdue, int pending) {}

    /** rate: tỷ lệ chuyên cần (%) = (có mặt + đi muộn) / (có mặt + đi muộn + vắng), vắng có phép không tính; null khi chưa có buổi nào. */
    public record Attendance(int present, int late, int absent, int excused, Integer ratePercent) {}

    public record ChapterTestScore(Long topicId, BigDecimal score, Integer maxScore, Integer percent) {}

    /** unitIndex 1-based của unit đang học (totalUnits + 1 khi đã xong hết, currentTopicId null); lagReasons rỗng = không tụt lại. */
    public record StudentProgress(Long userId, String displayName, int lessonPercent, int unitIndex, int totalUnits, Long currentTopicId,
                                  String currentTopicTitle, AssignmentCounts assignments, Integer avgPercent,
                                  List<ChapterTestScore> chapterTests, Attendance attendance, boolean lagging, List<String> lagReasons) {}

    /** medianUnitIndex: vị trí unit trung vị của lớp, mốc so sánh "chậm hơn lớp". */
    public record ProgressBoard(Long classId, Long courseId, Thresholds thresholds, int medianUnitIndex, List<StudentProgress> students) {}

    /** studentIds trống = cả lớp. */
    public record CreateUnlock(
            @NotNull(message = "Unit là bắt buộc") Long topicId,
            @Size(max = 200, message = "Tối đa 200 học viên") List<Long> studentIds) {}

    public record UnlockView(Long id, Long classId, Long topicId, Long userId, Long grantedBy, Instant grantedAt, Instant revokedAt) {}

    public record TeacherRow(Long userId, String displayName, int groupSessions, BigDecimal groupHours, int tutoringSessions,
                             BigDecimal tutoringHours, int totalSessions, BigDecimal totalHours) {}

    public record EditedSession(Long sessionId, Long classId, String className, Long teacherId, String teacherName, Instant startsAt,
                                int edits, Instant lastEditedAt) {}

    /** lastExportAt null = chưa từng xuất; editedSessions là buổi trong khoảng ngày bị sửa điểm danh sau mốc đó. */
    public record TeachingReport(LocalDate from, LocalDate to, Instant lastExportAt, List<TeacherRow> teachers,
                                 List<EditedSession> editedSessions) {}
}

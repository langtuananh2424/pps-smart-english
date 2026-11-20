package com.pps.smartenglish.classroom.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** FR-SES-01..04. */
public final class SessionDtos {

    private SessionDtos() {}

    private static final String MODE = "ONLINE|OFFLINE";
    private static final String STATUS = "PRESENT|ABSENT|LATE|EXCUSED";

    /** Buổi nhóm. taughtBy trống = người tạo (phải đang phụ trách lớp). repeatUntil: lặp hằng tuần đến hết ngày này. */
    public record CreateSession(
            @NotNull(message = "Giờ bắt đầu là bắt buộc") Instant startsAt,
            @NotNull(message = "Giờ kết thúc là bắt buộc") Instant endsAt,
            @NotNull(message = "Hình thức là bắt buộc") @Pattern(regexp = MODE, message = "Chỉ nhận ONLINE hoặc OFFLINE") String mode,
            @Size(max = 500, message = "Link tối đa 500 ký tự") String meetingUrl,
            Long taughtBy,
            LocalDate repeatUntil) {}

    /** Buổi kèm 1-1 do giáo vụ xếp: một học viên của lớp với một trợ giảng của lớp. */
    public record CreateTutoring(
            @NotNull(message = "Học viên là bắt buộc") Long studentId,
            @NotNull(message = "Trợ giảng là bắt buộc") Long assistantId,
            @NotNull(message = "Giờ bắt đầu là bắt buộc") Instant startsAt,
            @NotNull(message = "Giờ kết thúc là bắt buộc") Instant endsAt,
            @NotNull(message = "Hình thức là bắt buộc") @Pattern(regexp = MODE, message = "Chỉ nhận ONLINE hoặc OFFLINE") String mode,
            @Size(max = 500, message = "Link tối đa 500 ký tự") String meetingUrl) {}

    public record PatchSession(
            Instant startsAt,
            Instant endsAt,
            @Pattern(regexp = MODE, message = "Chỉ nhận ONLINE hoặc OFFLINE") String mode,
            @Size(max = 500, message = "Link tối đa 500 ký tự") String meetingUrl) {}

    /** myStatus chỉ có khi người xem là học viên; marked: buổi đã có điểm danh (không đổi được nữa). */
    public record SessionView(Long id, Long classId, String kind, Long studentId, String mode, Instant startsAt, Instant endsAt,
                              String meetingUrl, Long taughtBy, String taughtByName, String status, boolean marked, String myStatus) {}

    public record MarkInput(
            @NotNull(message = "Người được điểm danh là bắt buộc") Long userId,
            @NotNull(message = "Trạng thái là bắt buộc") @Pattern(regexp = STATUS, message = "Chỉ nhận PRESENT, ABSENT, LATE hoặc EXCUSED") String status) {}

    public record MarkRequest(@NotEmpty(message = "Cần ít nhất một dòng") @Size(max = 200, message = "Tối đa 200 dòng") List<@Valid MarkInput> marks) {}

    public record AttendanceRow(Long userId, String displayName, String status, Long markedBy, Instant markedAt) {}

    public record ChangeView(Long userId, String oldStatus, String newStatus, Long changedBy, Instant changedAt) {}

    public record SettingsView(int reminderMinutes) {}

    public record UpdateSettings(
            @NotNull(message = "Số phút là bắt buộc") @Min(value = 5, message = "Tối thiểu 5 phút") @Max(value = 10080, message = "Tối đa 7 ngày") Integer reminderMinutes) {}
}

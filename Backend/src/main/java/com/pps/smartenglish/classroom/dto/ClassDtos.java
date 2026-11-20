package com.pps.smartenglish.classroom.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** FR-CLS-01..04. */
public final class ClassDtos {

    private ClassDtos() {}

    private static final String MODE = "ONLINE|OFFLINE";

    public record CreateClass(
            @NotNull(message = "Khóa học là bắt buộc") Long courseId,
            @NotBlank(message = "Tên lớp là bắt buộc") @Size(max = 150, message = "Tên lớp tối đa 150 ký tự") String name,
            @NotNull(message = "Ngày bắt đầu là bắt buộc") LocalDate startsOn,
            @NotNull(message = "Ngày kết thúc là bắt buộc") LocalDate endsOn,
            @NotNull(message = "Hình thức là bắt buộc") @Pattern(regexp = MODE, message = "Chỉ nhận ONLINE hoặc OFFLINE") String mode,
            @NotNull(message = "Sĩ số là bắt buộc") @Min(value = 1, message = "Sĩ số phải từ 1 trở lên") Integer capacity) {}

    public record PatchClass(
            @Size(max = 150, message = "Tên lớp tối đa 150 ký tự") String name,
            LocalDate startsOn,
            LocalDate endsOn,
            @Pattern(regexp = MODE, message = "Chỉ nhận ONLINE hoặc OFFLINE") String mode,
            @Min(value = 1, message = "Sĩ số phải từ 1 trở lên") Integer capacity) {}

    public record Person(Long userId, String displayName) {}

    public record ClassView(Long id, Long courseId, String courseTitle, String name, LocalDate startsOn, LocalDate endsOn,
                            String mode, int capacity, String status, long activeMembers, Person teacher) {}

    public record StaffView(Long id, Long userId, String displayName, String role, LocalDate assignedFrom, LocalDate assignedTo,
                            boolean active) {}

    public record MemberView(Long userId, String displayName, String email, String status, Instant joinedAt, Instant leftAt) {}

    /** members null khi người xem là học viên của lớp (chỉ nhân sự thấy danh sách lớp). */
    public record ClassDetail(ClassView info, List<StaffView> staff, List<MemberView> members) {}

    public record AssignMember(@NotNull(message = "Học viên là bắt buộc") Long userId) {}

    public record TransferMember(@NotNull(message = "Lớp đích là bắt buộc") Long toClassId) {}

    public record AssignStaff(@NotNull(message = "Người phụ trách là bắt buộc") Long userId, LocalDate from, LocalDate to) {}

    public record WaitingRow(Long enrollmentId, Long userId, String displayName, String email, Long courseId, String courseTitle,
                             Instant purchasedAt) {}

    public record HistoryRow(Long classId, String className, String status, Instant joinedAt, Instant leftAt) {}
}

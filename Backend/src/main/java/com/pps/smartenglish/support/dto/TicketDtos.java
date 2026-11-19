package com.pps.smartenglish.support.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** FR-SUP-02: ticket và chat học viên với nhân viên. */
public final class TicketDtos {

    private TicketDtos() {}

    public record CreateTicket(
            @NotBlank(message = "Bắt buộc") @Size(max = 200, message = "Tối đa 200 ký tự") String subject,
            @NotBlank(message = "Bắt buộc") @Size(max = 2000, message = "Tối đa 2000 ký tự") String message) {}

    public record SendMessage(@NotBlank(message = "Bắt buộc") @Size(max = 2000, message = "Tối đa 2000 ký tự") String body) {}

    public record MessageView(Long id, boolean fromStaff, String body, Instant createdAt) {}

    /** Phía học viên: chỉ biết đã có người nhận xử lý hay chưa, không lộ danh tính nhân viên. */
    public record TicketView(Long id, String subject, String status, boolean assigned, Instant lastMessageAt, Instant createdAt) {}

    public record TicketDetail(TicketView ticket, List<MessageView> messages) {}

    /** Hiện khi tạo ticket để giao diện báo ngoài giờ làm việc (FR-SUP-02). */
    public record Info(boolean inHours, String etaMessage, String zaloUrl, String hotline) {}

    public record CreatedTicket(TicketView ticket, Info info) {}

    public record StaffTicketRow(Long id, String subject, String status, Long studentId, String studentEmail, String studentName,
                                 Long assignedTo, Instant lastMessageAt, Instant createdAt) {}

    public record StaffTicketDetail(StaffTicketRow ticket, List<MessageView> messages) {}

    public record HourDto(@NotNull(message = "Bắt buộc") @Min(value = 1, message = "1 = thứ Hai") @Max(value = 7, message = "7 = Chủ nhật") Integer dayOfWeek,
                          @NotBlank(message = "Bắt buộc") String open,
                          @NotBlank(message = "Bắt buộc") String close) {}

    /** hours rỗng = chưa cài giờ làm việc (luôn trong giờ). Có hours thì bắt buộc timezone hợp lệ (vd. Asia/Ho_Chi_Minh). */
    public record UpdateSettings(@Size(max = 64) String timezone, @Size(max = 300) String etaMessage, @Size(max = 300) String zaloUrl,
                                 @Size(max = 50) String hotline, @Valid List<HourDto> hours) {}

    public record SettingsView(String timezone, String etaMessage, String zaloUrl, String hotline, List<HourDto> hours) {}
}

package com.pps.smartenglish.support.dto;

import com.pps.smartenglish.ai.service.AiQuotaService.Overview;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** FR-SUP-03. Mọi thao tác thay đổi dữ liệu phải kèm lý do. */
public final class SupportDtos {

    private SupportDtos() {}

    public record StudentRow(long id, String email, String displayName, String status, boolean emailVerified) {}

    public record EnrollmentRow(long courseId, String courseTitle, String status, Instant startsAt, Instant expiresAt) {}

    public record OrderRow(String code, String status, long totalVnd, Instant paidAt, Instant createdAt) {}

    /** Không có doanh thu tổng (FR-SUP-03); ticket sẽ thêm khi phân hệ ticket (FR-SUP-02) có. */
    public record StudentDetail(StudentRow student, Instant createdAt, List<EnrollmentRow> enrollments,
                                List<OrderRow> orders, Overview quota) {}

    public record ReasonRequest(@NotBlank(message = "Bắt buộc") @Size(max = 500, message = "Tối đa 500 ký tự") String reason) {}

    public record BonusRequest(
            @NotNull(message = "Bắt buộc") @Min(value = 1, message = "Tối thiểu 1 lượt") @Max(value = 500, message = "Tối đa 500 lượt mỗi lần") Integer amount,
            @NotBlank(message = "Bắt buộc") @Size(max = 500, message = "Tối đa 500 ký tự") String reason) {}

    public record BonusResult(int amount, int pool) {}

    public record ActivateResult(String orderCode, long userId) {}
}

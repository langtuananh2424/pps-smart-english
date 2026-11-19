package com.pps.smartenglish.commerce.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** FR-COM-07: quản lý gói bán và mức thời hạn (D-127). */
public final class ProductAdminDtos {

    private ProductAdminDtos() {}

    /** LEVEL: đúng một levelId và accessDays. AI_ADDON: aiQuota, không levelIds, không accessDays. */
    public record Create(
            @NotNull(message = "Bắt buộc") @Pattern(regexp = "LEVEL|AI_ADDON", message = "Chỉ nhận LEVEL hoặc AI_ADDON") String type,
            @NotBlank(message = "Bắt buộc") @Size(max = 200, message = "Tối đa 200 ký tự") String name,
            @NotNull(message = "Bắt buộc") @Min(value = 0, message = "Không âm") Long priceVnd,
            @Min(value = 1, message = "Tối thiểu 1 ngày") Integer accessDays,
            @Min(value = 0, message = "Không âm") Integer aiQuota,
            List<Long> levelIds) {}

    /** Trường vắng mặt (null) giữ nguyên. Đổi accessDays, aiQuota chỉ được khi chưa có đơn hoặc link dùng gói. */
    public record Patch(
            @Size(max = 200, message = "Tối đa 200 ký tự") String name,
            @Min(value = 0, message = "Không âm") Long priceVnd,
            @Min(value = 1, message = "Tối thiểu 1 ngày") Integer accessDays,
            @Min(value = 0, message = "Không âm") Integer aiQuota,
            @Pattern(regexp = "ACTIVE|INACTIVE", message = "Chỉ nhận ACTIVE hoặc INACTIVE") String status) {}

    public record View(Long id, String type, String name, long priceVnd, Integer accessDays, Integer aiQuota, String status,
                       List<Long> levelIds) {}
}

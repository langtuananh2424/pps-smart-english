package com.pps.smartenglish.commerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

public final class ActivationLinkDtos {

    private ActivationLinkDtos() {}

    /** url chứa mã thô và chỉ xuất hiện đúng một lần, ở phản hồi tạo link. */
    public record Created(Long id, String url) {}

    public record Preview(String productName, String productType, Integer accessDays, Integer aiQuota) {}

    /** expiresAt: hạn muộn nhất của quyền học vừa cấp; vắng mặt nếu gói không giới hạn hoặc chỉ nạp lượt. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Redeemed(String productName, Instant expiresAt) {}

    public record Revoked(Long id, Instant revokedAt) {}
}

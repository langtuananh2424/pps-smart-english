package com.pps.smartenglish.retention.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class PushDtos {

    public static final int MAX_ENDPOINT = 1000;

    private PushDtos() {}

    public record Keys(
            @NotBlank(message = "Bắt buộc") @Size(max = 200, message = "Tối đa 200 ký tự") String p256dh,
            @NotBlank(message = "Bắt buộc") @Size(max = 200, message = "Tối đa 200 ký tự") String auth) {}

    public record SubscribeRequest(
            @NotBlank(message = "Bắt buộc") @Size(max = MAX_ENDPOINT, message = "Tối đa 1000 ký tự") String endpoint,
            @NotNull(message = "Bắt buộc") @Valid Keys keys) {}

    public record UnsubscribeRequest(@NotBlank(message = "Bắt buộc") @Size(max = MAX_ENDPOINT, message = "Tối đa 1000 ký tự") String endpoint) {}

    public record Subscribed(Long id) {}

    /** publicKey null khi chưa cấu hình VAPID. */
    public record VapidKey(String publicKey) {}
}

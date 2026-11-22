package com.pps.smartenglish.media.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.Map;

public final class MediaDtos {

    private MediaDtos() {}

    public record UploadRequest(@NotBlank String contentType) {}

    /** Quyền tải lên: POST multipart tới url với các fields đặt trước field `file`; xong gọi complete. */
    public record UploadPermit(Long assetId, String url, Map<String, String> fields, long maxBytes, String contentType,
                               Instant expiresAt) {}

    /** Ảnh dùng được: url ký có hạn, không bao giờ lộ key thô (NFR-SEC-02). */
    public record MediaFile(Long id, String url, int width, int height) {}
}

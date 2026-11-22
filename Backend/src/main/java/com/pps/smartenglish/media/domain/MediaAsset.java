package com.pps.smartenglish.media.domain;

import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Một file media của CMS. PENDING đến khi máy chủ xử lý xong; chỉ READY mới dùng được. */
@Entity
@Table(name = "media_assets")
public class MediaAsset extends Auditable {

    public static final String IMAGE = "IMAGE";
    public static final String PENDING = "PENDING";
    public static final String READY = "READY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String kind;

    @Column(nullable = false)
    private String status = PENDING;

    @Column(name = "upload_key")
    private String uploadKey;

    @Column(name = "storage_key")
    private String storageKey;

    @Column(name = "upload_expires_at")
    private Instant uploadExpiresAt;

    @Column(name = "max_bytes")
    private Long maxBytes;

    private String mime;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    private Integer width;
    private Integer height;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private Long uploadedBy;

    protected MediaAsset() {}

    public static MediaAsset pendingImage(long uploadedBy, String uploadKey, long maxBytes, Instant expiresAt) {
        var a = new MediaAsset();
        a.kind = IMAGE;
        a.uploadedBy = uploadedBy;
        a.uploadKey = uploadKey;
        a.maxBytes = maxBytes;
        a.uploadExpiresAt = expiresAt;
        return a;
    }

    public void ready(String storageKey, String mime, long sizeBytes, int width, int height) {
        this.status = READY;
        this.storageKey = storageKey;
        this.uploadKey = null;
        this.uploadExpiresAt = null;
        this.maxBytes = null;
        this.mime = mime;
        this.sizeBytes = sizeBytes;
        this.width = width;
        this.height = height;
    }

    public Long getId() { return id; }
    public String getKind() { return kind; }
    public String getStatus() { return status; }
    public boolean isReady() { return READY.equals(status); }
    public String getUploadKey() { return uploadKey; }
    public String getStorageKey() { return storageKey; }
    public Instant getUploadExpiresAt() { return uploadExpiresAt; }
    public Long getMaxBytes() { return maxBytes; }
    public String getMime() { return mime; }
    public Long getSizeBytes() { return sizeBytes; }
    public Integer getWidth() { return width; }
    public Integer getHeight() { return height; }
    public Long getUploadedBy() { return uploadedBy; }
}

package com.pps.smartenglish.media.service;

import com.pps.smartenglish.media.config.MediaProperties;
import com.pps.smartenglish.media.domain.MediaAsset;
import com.pps.smartenglish.media.dto.MediaDtos.UploadPermit;
import com.pps.smartenglish.media.repository.MediaAssetRepository;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.storage.ObjectStorage;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * D-156: video mp4 làm đề (media_assets kind VIDEO). Cùng luồng audio: createUpload (presigned POST thẳng lên kho, file không đi qua
 * RAM máy chủ, kho tự chặn quá maxBytes) -> complete (kiểm dung lượng và chữ ký mp4).
 */
@Service
public class VideoMediaService {

    public static final String MP4 = "video/mp4";

    public record VideoFile(Long id, String mime, Long sizeBytes) {}

    private final MediaAssetRepository assets;
    private final ObjectStorage storage;
    private final MediaProperties media;
    private final Clock clock;

    VideoMediaService(MediaAssetRepository assets, ObjectStorage storage, MediaProperties media, Clock clock) {
        this.assets = assets;
        this.storage = storage;
        this.media = media;
        this.clock = clock;
    }

    public UploadPermit createUpload(long userId, String contentType) {
        return permit(userId, contentType, media.video().maxBytes(), "media/video/");
    }

    /** D-171: video bài giảng, trần dung lượng riêng; xác nhận dùng chung complete. */
    public UploadPermit createLessonUpload(long userId, String contentType) {
        return permit(userId, contentType, media.lessonVideo().maxBytes(), "media/lesson-video/");
    }

    private UploadPermit permit(long userId, String contentType, long max, String prefix) {
        if (!MP4.equals(contentType)) {
            throw bad("video_type_unsupported", "Chỉ nhận video MP4");
        }
        Instant expires = Instant.now(clock).plus(media.uploadTtl());
        String key = prefix + UUID.randomUUID();
        var asset = assets.save(MediaAsset.pendingVideo(userId, key, max, expires));
        var form = storage.presignPost(key, contentType, max, media.uploadTtl());
        return new UploadPermit(asset.getId(), form.url(), form.fields(), max, contentType, expires);
    }

    /** Idempotent; chỉ người tải lên được xác nhận. */
    public VideoFile complete(long userId, long id) {
        var asset = assets.findById(id).filter(a -> a.getUploadedBy() == userId && MediaAsset.VIDEO.equals(a.getKind()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "media_not_found", "Không tìm thấy video"));
        if (asset.isReady()) {
            return file(asset);
        }
        if (!Instant.now(clock).isBefore(asset.getUploadExpiresAt())) {
            throw new ApiException(HttpStatus.GONE, "upload_expired", "Đã quá hạn tải lên, hãy xin quyền tải lên lại");
        }
        String key = asset.getUploadKey();
        var meta = storage.head(key).orElseThrow(() -> bad("video_missing", "Chưa thấy file video được tải lên"));
        if (meta.size() > asset.getMaxBytes()) {
            storage.delete(key);
            throw bad("video_too_large", "Video vượt dung lượng cho phép");
        }
        if (!isMp4(storage.readHead(key, 12))) {
            storage.delete(key);
            throw bad("video_invalid", "File không phải video MP4");
        }
        int durationMs;
        try {
            durationMs = Mp4Duration.millis(storage, key, meta.size());
        } catch (ApiException e) {
            storage.delete(key); // thời lượng là điều kiện của quiz video nên file không đọc được thì từ chối
            throw e;
        } catch (RuntimeException e) {
            storage.delete(key);
            throw bad("video_duration_unreadable", "Không đọc được thời lượng video MP4");
        }
        asset.readyVideo(MP4, meta.size(), durationMs);
        return file(assets.save(asset));
    }

    /** Video READY theo id; không phải video hoặc chưa xong thì 400 (bài giảng, trần 300 MB). */
    public MediaAsset requireVideo(long id) {
        return requireVideo(id, Long.MAX_VALUE);
    }

    /** Như requireVideo nhưng giữ trần dung lượng của video làm đề (D-156), không nhận video bài giảng lớn hơn. */
    public MediaAsset requireQuizVideo(long id) {
        return requireVideo(id, media.video().maxBytes());
    }

    private MediaAsset requireVideo(long id, long maxBytes) {
        return assets.findById(id).filter(a -> a.isReady() && MediaAsset.VIDEO.equals(a.getKind())
                        && (a.getSizeBytes() == null || a.getSizeBytes() <= maxBytes))
                .orElseThrow(() -> bad("media_not_ready", "Video chưa tải xong hoặc không tồn tại: " + id));
    }

    /** Khóa kho của video READY; dùng khi xuất bản bài STORAGE. */
    public boolean isReadyVideoKey(String key) {
        return assets.findByStorageKey(key).filter(a -> a.isReady() && MediaAsset.VIDEO.equals(a.getKind())).isPresent();
    }

    /** Thời lượng (giây, làm tròn lên) của video READY; không có thì empty. */
    public java.util.Optional<Integer> durationSec(long id) {
        return assets.findById(id).filter(a -> a.isReady() && MediaAsset.VIDEO.equals(a.getKind()) && a.getDurationMs() != null)
                .map(a -> (a.getDurationMs() + 999) / 1000);
    }

    /** Link xem có hạn (app.media.url-ttl) của các video READY; id lạ không có trong kết quả. */
    public Map<Long, String> signedUrls(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return assets.findAllById(ids).stream().filter(a -> a.isReady() && MediaAsset.VIDEO.equals(a.getKind()))
                .collect(Collectors.toMap(MediaAsset::getId, a -> storage.presignGet(a.getStorageKey(), media.urlTtl())));
    }

    /** Major brand video MP4 thường gặp; M4A/M4B (audio), qt (MOV), 3gp... không nằm trong danh sách nên bị từ chối. */
    private static final Set<String> VIDEO_BRANDS = Set.of("isom", "iso2", "iso3", "iso4", "iso5", "iso6", "mp41", "mp42", "avc1",
            "M4V ", "M4VH", "M4VP", "dash", "mmp4", "XAVC");

    /** MP4: "ftyp" ở byte 4..7 và major brand ở byte 8..11 thuộc VIDEO_BRANDS. */
    static boolean isMp4(byte[] h) {
        return h.length >= 12 && h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p'
                && VIDEO_BRANDS.contains(new String(h, 8, 4, StandardCharsets.ISO_8859_1));
    }

    private static VideoFile file(MediaAsset a) {
        return new VideoFile(a.getId(), a.getMime(), a.getSizeBytes());
    }

    private static ApiException bad(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}

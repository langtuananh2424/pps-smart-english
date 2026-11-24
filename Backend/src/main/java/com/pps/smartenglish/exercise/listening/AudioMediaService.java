package com.pps.smartenglish.exercise.listening;

import com.pps.smartenglish.exercise.listening.ListeningDtos.AudioFile;
import com.pps.smartenglish.media.config.MediaProperties;
import com.pps.smartenglish.media.domain.MediaAsset;
import com.pps.smartenglish.media.dto.MediaDtos.UploadPermit;
import com.pps.smartenglish.media.repository.MediaAssetRepository;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.storage.ObjectStorage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * D-135: audio của part nằm ở media_assets (kind AUDIO) trong kho riêng tư. Luồng: createUpload (presigned POST vào khóa cuối) ->
 * complete (kiểm dung lượng và chữ ký file). Client học viên không bao giờ nhận key thô, chỉ link ký ngắn hạn qua AudioPlayService.
 */
@Service
public class AudioMediaService {

    private static final Set<String> TYPES = Set.of("audio/mpeg", "audio/mp4", "audio/wav", "audio/ogg");

    private final MediaAssetRepository assets;
    private final ObjectStorage storage;
    private final MediaProperties media;
    private final ListeningProperties props;
    private final Clock clock;

    AudioMediaService(MediaAssetRepository assets, ObjectStorage storage, MediaProperties media, ListeningProperties props, Clock clock) {
        this.assets = assets;
        this.storage = storage;
        this.media = media;
        this.props = props;
        this.clock = clock;
    }

    public UploadPermit createUpload(long userId, String contentType) {
        if (!TYPES.contains(contentType)) {
            throw bad("audio_type_unsupported", "Chỉ nhận audio MP3, M4A, WAV hoặc OGG");
        }
        Instant expires = Instant.now(clock).plus(media.uploadTtl());
        String key = "media/audio/" + UUID.randomUUID();
        var asset = assets.save(MediaAsset.pendingAudio(userId, key, props.audioMaxBytes(), expires));
        var form = storage.presignPost(key, contentType, props.audioMaxBytes(), media.uploadTtl());
        return new UploadPermit(asset.getId(), form.url(), form.fields(), props.audioMaxBytes(), contentType, expires);
    }

    /** Idempotent. durationMs do CMS khai (không giải mã được audio trên máy chủ); chỉ dùng để chọn hạn link nghe. */
    public AudioFile complete(long userId, long id, Integer durationMs) {
        var asset = assets.findById(id).filter(a -> a.getUploadedBy() == userId && MediaAsset.AUDIO.equals(a.getKind()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "media_not_found", "Không tìm thấy audio"));
        if (asset.isReady()) {
            return file(asset);
        }
        if (durationMs != null && (durationMs < 1 || durationMs > props.audioMaxDurationMs())) {
            throw ApiException.validation("durationMs", "Độ dài audio ngoài khoảng cho phép");
        }
        if (!Instant.now(clock).isBefore(asset.getUploadExpiresAt())) {
            throw new ApiException(HttpStatus.GONE, "upload_expired", "Đã quá hạn tải lên, hãy xin quyền tải lên lại");
        }
        String key = asset.getUploadKey();
        var meta = storage.head(key).orElseThrow(() -> bad("audio_missing", "Chưa thấy file audio được tải lên"));
        if (meta.size() > asset.getMaxBytes()) {
            storage.delete(key);
            throw bad("audio_too_large", "Audio vượt dung lượng cho phép");
        }
        String mime = sniff(storage.readHead(key, 12));
        if (mime == null) {
            storage.delete(key);
            throw bad("audio_invalid", "File không phải audio MP3, M4A, WAV hoặc OGG");
        }
        asset.readyAudio(mime, meta.size(), durationMs);
        return file(assets.save(asset));
    }

    /** Audio READY theo id; không phải audio hoặc chưa xong thì 400 (dùng khi gắn vào nhóm câu). */
    public MediaAsset requireAudio(long id) {
        return ready(id).orElseThrow(() -> bad("media_not_ready", "Audio chưa tải xong hoặc không tồn tại: " + id));
    }

    public Optional<MediaAsset> ready(long id) {
        return assets.findById(id).filter(a -> a.isReady() && MediaAsset.AUDIO.equals(a.getKind()));
    }

    public String signedUrl(MediaAsset audio, Duration ttl) {
        return storage.presignGet(audio.getStorageKey(), ttl);
    }

    /** Độ dài audio (ms, có thể null) của các id READY; id lạ không có trong kết quả. */
    public Map<Long, Optional<Integer>> durations(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return assets.findAllById(ids).stream().filter(a -> a.isReady() && MediaAsset.AUDIO.equals(a.getKind()))
                .collect(Collectors.toMap(MediaAsset::getId, a -> Optional.ofNullable(a.getDurationMs())));
    }

    private static AudioFile file(MediaAsset a) {
        return new AudioFile(a.getId(), a.getMime(), a.getSizeBytes(), a.getDurationMs());
    }

    /** Chữ ký đầu file: MP3 (ID3 hoặc khung 0xFFEx), M4A (ftyp), WAV (RIFF....WAVE), OGG (OggS). null = không nhận. */
    static String sniff(byte[] h) {
        if (h.length >= 3 && h[0] == 'I' && h[1] == 'D' && h[2] == '3' || h.length >= 2 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xE0) == 0xE0) {
            return "audio/mpeg";
        }
        if (h.length >= 8 && h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p') {
            return "audio/mp4";
        }
        if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F' && h[8] == 'W' && h[9] == 'A' && h[10] == 'V'
                && h[11] == 'E') {
            return "audio/wav";
        }
        if (h.length >= 4 && h[0] == 'O' && h[1] == 'g' && h[2] == 'g' && h[3] == 'S') {
            return "audio/ogg";
        }
        return null;
    }

    private static ApiException bad(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}

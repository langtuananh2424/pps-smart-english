package com.pps.smartenglish.media.service;

import com.pps.smartenglish.media.config.MediaProperties;
import com.pps.smartenglish.media.domain.MediaAsset;
import com.pps.smartenglish.media.dto.MediaDtos.MediaFile;
import com.pps.smartenglish.media.dto.MediaDtos.UploadPermit;
import com.pps.smartenglish.media.repository.MediaAssetRepository;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.storage.ObjectStorage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * D-134: tải ảnh lên kho riêng tư. Luồng: createImageUpload (presigned POST vào khóa tạm) -> complete (kiểm, đổi WebP,
 * thu nhỏ, ghi khóa chính thức, xóa khóa tạm). Chỉ người tải lên mới hoàn tất được lượt của mình.
 */
@Service
public class MediaService {

    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/gif");
    private static final String WEBP = "image/webp";

    private final MediaAssetRepository assets;
    private final ObjectStorage storage;
    private final ImageConverter converter;
    private final MediaProperties props;
    private final Clock clock;

    MediaService(MediaAssetRepository assets, ObjectStorage storage, ImageConverter converter, MediaProperties props, Clock clock) {
        this.assets = assets;
        this.storage = storage;
        this.converter = converter;
        this.props = props;
        this.clock = clock;
    }

    public UploadPermit createImageUpload(long userId, String contentType) {
        if (!IMAGE_TYPES.contains(contentType)) {
            throw bad("image_type_unsupported", "Chỉ nhận ảnh JPEG, PNG hoặc GIF");
        }
        long maxBytes = props.image().maxUploadBytes();
        Instant expires = Instant.now(clock).plus(props.uploadTtl());
        String key = "media/tmp/" + UUID.randomUUID();
        var asset = assets.save(MediaAsset.pendingImage(userId, key, maxBytes, expires));
        var form = storage.presignPost(key, contentType, maxBytes, props.uploadTtl());
        return new UploadPermit(asset.getId(), form.url(), form.fields(), maxBytes, contentType, expires);
    }

    /** Idempotent: đã xong thì trả lại ảnh, không xử lý lần hai. */
    public MediaFile complete(long userId, long id) {
        var asset = assets.findById(id).filter(a -> a.getUploadedBy() == userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "media_not_found", "Không tìm thấy ảnh"));
        if (asset.isReady()) {
            return file(asset);
        }
        if (!Instant.now(clock).isBefore(asset.getUploadExpiresAt())) {
            throw new ApiException(HttpStatus.GONE, "upload_expired", "Đã quá hạn tải lên, hãy xin quyền tải lên lại");
        }
        String tmp = asset.getUploadKey();
        var meta = storage.head(tmp).orElseThrow(() -> bad("image_missing", "Chưa thấy file ảnh được tải lên"));
        if (meta.size() > asset.getMaxBytes()) {
            storage.delete(tmp);
            throw bad("image_too_large", "Ảnh vượt dung lượng cho phép");
        }
        ImageConverter.Converted out;
        try (InputStream in = storage.open(tmp)) {
            out = converter.convert(in.readAllBytes());
        } catch (ImageConverter.InvalidImageException e) {
            storage.delete(tmp);
            throw bad("image_invalid", e.getMessage());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String finalKey = "media/images/" + UUID.randomUUID() + ".webp";
        storage.put(finalKey, out.webp(), WEBP);
        storage.delete(tmp);
        asset.ready(finalKey, WEBP, out.webp().length, out.width(), out.height());
        return file(assets.save(asset));
    }

    /** Các ảnh READY theo id; id lạ, chưa xong hoặc không phải ảnh thì không có trong kết quả. */
    public Map<Long, MediaFile> files(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return assets.findAllById(ids).stream().filter(a -> a.isReady() && MediaAsset.IMAGE.equals(a.getKind()))
                .collect(Collectors.toMap(MediaAsset::getId, this::file));
    }

    /** Dùng khi gắn vào câu hỏi: mọi id phải là ảnh READY, nếu không thì 400 kèm id lỗi. */
    public Map<Long, MediaFile> requireImages(Collection<Long> ids) {
        var found = files(ids);
        List<Long> missing = ids.stream().filter(i -> !found.containsKey(i)).distinct().toList();
        if (!missing.isEmpty()) {
            throw bad("media_not_ready", "Ảnh chưa tải xong hoặc không tồn tại: " + missing);
        }
        return found;
    }

    private MediaFile file(MediaAsset a) {
        return new MediaFile(a.getId(), storage.presignGet(a.getStorageKey(), props.urlTtl()), a.getWidth(), a.getHeight());
    }

    private static ApiException bad(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}

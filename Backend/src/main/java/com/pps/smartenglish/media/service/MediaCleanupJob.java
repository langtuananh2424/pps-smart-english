package com.pps.smartenglish.media.service;

import com.pps.smartenglish.media.config.MediaProperties;
import com.pps.smartenglish.media.repository.MediaAssetRepository;
import com.pps.smartenglish.shared.storage.ObjectStorage;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** D-134: xóa file tạm của lượt tải lên không được xác nhận sau expireAfter. Tắt bằng app.media.cleanup-enabled=false. */
@Component
public class MediaCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(MediaCleanupJob.class);
    private static final int BATCH = 100;

    private final MediaAssetRepository assets;
    private final ObjectStorage storage;
    private final MediaProperties props;
    private final Clock clock;

    MediaCleanupJob(MediaAssetRepository assets, ObjectStorage storage, MediaProperties props, Clock clock) {
        this.assets = assets;
        this.storage = storage;
        this.props = props;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.media.cleanup-poll-ms:3600000}")
    void run() {
        if (props.cleanupEnabled()) {
            tick();
        }
    }

    /** Trả số mục đã dọn. Lỗi kho ở một mục không chặn mục khác (lần sau thử lại). */
    public int tick() {
        int done = 0;
        for (var a : assets.findStalePending(Instant.now(clock).minus(props.expireAfter()), PageRequest.of(0, BATCH))) {
            try {
                storage.delete(a.getUploadKey());
                assets.delete(a);
                done++;
            } catch (RuntimeException e) {
                log.warn("Dọn ảnh tạm {} lỗi: {}", a.getId(), e.toString());
            }
        }
        return done;
    }
}

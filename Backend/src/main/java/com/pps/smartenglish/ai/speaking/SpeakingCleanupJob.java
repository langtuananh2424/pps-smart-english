package com.pps.smartenglish.ai.speaking;

import com.pps.smartenglish.shared.storage.ObjectStorage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * D-27, D-43: xóa file chưa xác nhận (quá hạn tải lên + expire-after) và audio đã chấm quá audio-retention-days, giữ kết quả.
 * Tắt bằng app.speaking.cleanup-enabled=false (test gọi tick() trực tiếp).
 */
@Component
public class SpeakingCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(SpeakingCleanupJob.class);
    private static final int BATCH = 100;

    private final SpeakingSubmissionRepository submissions;
    private final ObjectStorage storage;
    private final SpeakingProperties props;
    private final Clock clock;

    SpeakingCleanupJob(SpeakingSubmissionRepository submissions, ObjectStorage storage, SpeakingProperties props, Clock clock) {
        this.submissions = submissions;
        this.storage = storage;
        this.props = props;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.speaking.cleanup-poll-ms:300000}")
    void run() {
        if (props.cleanupEnabled()) {
            tick();
        }
    }

    /** Trả tổng số mục đã dọn. Lỗi kho ở một mục không chặn các mục khác (lần sau thử lại). */
    public int tick() {
        Instant now = Instant.now(clock);
        int done = 0;
        for (var s : submissions.findStaleUploads(now.minus(props.expireAfter()), PageRequest.of(0, BATCH))) {
            try {
                storage.delete(s.getObjectKey());
                submissions.expire(s.getId(), now);
                done++;
            } catch (RuntimeException e) {
                log.warn("Dọn upload quá hạn {} lỗi: {}", s.getId(), e.toString());
            }
        }
        Instant cutoff = now.minus(Duration.ofDays(props.audioRetentionDays()));
        for (var s : submissions.findAudioDue(cutoff, PageRequest.of(0, BATCH))) {
            try {
                storage.delete(s.getObjectKey());
                submissions.markAudioDeleted(s.getId(), now);
                done++;
            } catch (RuntimeException e) {
                log.warn("Xóa audio quá hạn lưu {} lỗi: {}", s.getId(), e.toString());
            }
        }
        return done;
    }
}

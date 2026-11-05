package com.pps.smartenglish.lms.service;

import com.pps.smartenglish.lms.dto.ProgressRequest;
import com.pps.smartenglish.lms.dto.ProgressResponse;
import com.pps.smartenglish.lms.repository.LessonProgressRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-LMS-03. XP và activity_days sẽ nối ở đây khi các module đó có. */
@Service
public class LessonProgressService {

    /** Chu kỳ báo 15 s + 5 s sai số; delta lớn hơn bị cắt để tua nhanh không tính là xem. */
    static final int MAX_DELTA_SEC = 20;

    private final LessonGate gate;
    private final LessonProgressRepository progress;
    private final Clock clock;

    LessonProgressService(LessonGate gate, LessonProgressRepository progress, Clock clock) {
        this.gate = gate;
        this.progress = progress;
        this.clock = clock;
    }

    @Transactional
    public ProgressResponse record(long userId, Long lessonId, ProgressRequest req) {
        var lesson = gate.require(lessonId, userId);
        int duration = lesson.durationSec() == null ? 0 : lesson.durationSec();
        try {
            progress.createIfAbsent(userId, lessonId);
        } catch (DataIntegrityViolationException ignored) {
            // request đồng thời đã tạo dòng trước; chỉ cần cộng dồn
        }
        progress.addProgress(userId, lessonId, Math.min(req.watchedDeltaSec(), MAX_DELTA_SEC), req.positionSec(),
                duration, Instant.now(clock));
        var p = progress.findByUserIdAndLessonId(userId, lessonId).orElseThrow();
        return new ProgressResponse(lessonId, p.getWatchedSec(), p.getMaxPositionSec(), p.getCompletedAt() != null,
                p.getCompletedAt());
    }
}

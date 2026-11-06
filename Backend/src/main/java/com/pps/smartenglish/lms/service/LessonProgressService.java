package com.pps.smartenglish.lms.service;

import com.pps.smartenglish.lms.dto.ProgressRequest;
import com.pps.smartenglish.lms.dto.ProgressResponse;
import com.pps.smartenglish.lms.repository.LessonProgressRepository;
import com.pps.smartenglish.shared.event.LessonCompleted;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-LMS-03. Phát LessonCompleted ở lần hoàn thành đầu tiên để retention tính streak và XP (FR-RET-01, 02). */
@Service
public class LessonProgressService {

    /** Chu kỳ báo 15 s + 5 s sai số; delta lớn hơn bị cắt để tua nhanh không tính là xem. */
    static final int MAX_DELTA_SEC = 20;

    private final LessonGate gate;
    private final LessonProgressRepository progress;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    LessonProgressService(LessonGate gate, LessonProgressRepository progress, Clock clock, ApplicationEventPublisher events) {
        this.gate = gate;
        this.progress = progress;
        this.clock = clock;
        this.events = events;
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
        Instant now = Instant.now(clock);
        progress.addProgress(userId, lessonId, Math.min(req.watchedDeltaSec(), MAX_DELTA_SEC), req.positionSec(), now);
        if (progress.markCompleted(userId, lessonId, duration, now) == 1) {
            events.publishEvent(new LessonCompleted(userId, lessonId)); // retention nghe sau commit
        }
        var p = progress.findByUserIdAndLessonId(userId, lessonId).orElseThrow();
        return new ProgressResponse(lessonId, p.getWatchedSec(), p.getMaxPositionSec(), p.getCompletedAt() != null,
                p.getCompletedAt());
    }
}

package com.pps.smartenglish.retention.service;

import com.pps.smartenglish.retention.config.RetentionProperties;
import com.pps.smartenglish.shared.event.AiGradingFinished;
import com.pps.smartenglish.shared.event.ExerciseScored;
import com.pps.smartenglish.shared.event.LessonCompleted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Nghe sự kiện của lms, exercise, ai SAU KHI nơi phát đã commit (hoặc ngay nếu không có transaction) và ghi bằng
 * transaction riêng. Lỗi ở đây chỉ được log, không bao giờ làm hỏng bài học, lần nộp hay job đã thành công.
 */
@Component
public class RetentionListener {

    static final String WRITING_GRADE = "WRITING_GRADE";
    private static final Logger log = LoggerFactory.getLogger(RetentionListener.class);

    private final StreakService streak;
    private final XpService xp;
    private final NotificationService notifications;
    private final RetentionProperties props;

    RetentionListener(StreakService streak, XpService xp, NotificationService notifications, RetentionProperties props) {
        this.streak = streak;
        this.xp = xp;
        this.notifications = notifications;
        this.props = props;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onLessonCompleted(LessonCompleted e) {
        safely("streak", () -> streak.recordActivity(e.userId()));
        safely("xp", () -> xp.grant(e.userId(), "LESSON", e.lessonId(), props.xp().lesson()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onExerciseScored(ExerciseScored e) {
        if (e.initial()) {
            safely("streak", () -> streak.recordActivity(e.userId()));
            safely("xp", () -> xp.grant(e.userId(), "EXERCISE", e.bankId(), props.xp().exercise()));
        }
        // sự kiện sau khi AI chấm xong câu viết lại cũng xét thưởng điểm cao, nhưng không tính thêm ngày học
        if (e.maxScore() > 0 && (long) e.score() * 100 >= (long) props.xp().highScorePercent() * e.maxScore()) {
            safely("xp-high", () -> xp.grant(e.userId(), "EXERCISE_HIGH", e.bankId(), props.xp().highScore()));
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAiGradingFinished(AiGradingFinished e) {
        if (WRITING_GRADE.equals(e.kind())) {
            safely("notify", () -> notifications.writingGraded(e.userId(), e.jobId()));
        }
    }

    private void safely(String what, Runnable action) {
        try {
            action.run();
        } catch (DataIntegrityViolationException e) {
            log.debug("Retention {}: bỏ qua do trùng (xử lý đồng thời)", what); // bên thua cuộc đã rollback
        } catch (RuntimeException e) {
            log.warn("Retention {} lỗi, bỏ qua: {}", what, e.getClass().getSimpleName()); // không ghi nội dung người dùng
        }
    }
}

package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.catalog.domain.ContentStatus;
import com.pps.smartenglish.catalog.domain.Course;
import com.pps.smartenglish.catalog.repository.CourseRepository;
import com.pps.smartenglish.exercise.domain.StudentAttempt;
import com.pps.smartenglish.exercise.dto.ExamStatus;
import com.pps.smartenglish.exercise.repository.StudentAttemptRepository;
import com.pps.smartenglish.lms.service.CourseProgressService;
import com.pps.smartenglish.shared.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-EXAM-02, D-35, D-46: điều kiện thi khóa (đã mua, đủ tiến độ, còn lượt) và trạng thái cho giao diện. */
@Service
public class ExamService {

    static final String ENROLLMENT_REQUIRED = "ENROLLMENT_REQUIRED";
    static final String PROGRESS = "PROGRESS";
    static final String ATTEMPTS_EXHAUSTED = "ATTEMPTS_EXHAUSTED";
    static final String NOT_CONFIGURED = "NOT_CONFIGURED";

    private final CourseRepository courses;
    private final StudentAttemptRepository attempts;
    private final CourseProgressService progress;
    private final Clock clock;

    ExamService(CourseRepository courses, StudentAttemptRepository attempts, CourseProgressService progress, Clock clock) {
        this.courses = courses;
        this.attempts = attempts;
        this.progress = progress;
        this.clock = clock;
    }

    /**
     * Gọi trong transaction của AttemptService.start. Bộ EXAM không là bài thi của khóa nào thì bỏ qua (chỉ chịu quy tắc
     * truy cập chung). Khóa dòng user trước khi đếm lượt để hai start song song không vượt số lượt.
     */
    void guardStart(long userId, long bankId) {
        var course = courses.findByExamBankId(bankId).orElse(null);
        if (course == null) {
            return;
        }
        attempts.lockUser(userId);
        requireEligible(userId, course);
        var open = attempts.findFirstByUserIdAndBankIdAndSubmittedAtIsNullOrderByIdDesc(userId, bankId);
        if (open.isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "exam_in_progress", "Bạn đang có một lượt thi chưa nộp",
                    Map.of("attemptId", open.get().getId()), null);
        }
        long used = attempts.countByUserIdAndBankId(userId, bankId);
        int allowed = 1 + course.getExamRetakes();
        if (used >= allowed) {
            throw new ApiException(HttpStatus.CONFLICT, "exam_attempts_exhausted", "Đã hết số lần thi",
                    Map.of("used", used, "allowed", allowed), null);
        }
    }

    /** Đề thi khóa chỉ lộ khi học viên đã bắt đầu một lượt chưa nộp, để không xem trước đề. */
    void guardView(long userId, long bankId) {
        var course = courses.findByExamBankId(bankId).orElse(null);
        if (course == null) {
            return;
        }
        requireEligible(userId, course);
        if (attempts.findFirstByUserIdAndBankIdAndSubmittedAtIsNullOrderByIdDesc(userId, bankId).isEmpty()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "exam_not_started", "Hãy bắt đầu lượt thi trước khi xem đề");
        }
    }

    private void requireEligible(long userId, Course course) {
        if (courses.findActive(userId, course.getId(), Instant.now(clock)).isEmpty()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "exam_requires_enrollment", "Cần mua khóa học để làm bài thi");
        }
        int percent = progress.compute(userId, course.getId()).percent();
        if (percent < course.getExamUnlockPercent()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "exam_locked", "Chưa học đủ tỷ lệ để mở bài thi",
                    Map.of("progressPercent", percent, "requiredPercent", course.getExamUnlockPercent()), null);
        }
    }

    /** D-36: khóa hết hạn vẫn xem được kết quả; chỉ unlocked=false. */
    @Transactional(readOnly = true)
    public ExamStatus status(long userId, long courseId) {
        Course course = courses.findById(courseId).filter(c -> ContentStatus.PUBLISHED.equals(c.getStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "course_not_found", "Không tìm thấy khóa học"));
        int required = course.getExamUnlockPercent();
        int allowed = 1 + course.getExamRetakes();
        int percent = progress.compute(userId, courseId).percent();
        Long bankId = course.getExamBankId();
        if (bankId == null) {
            return new ExamStatus(null, percent, required, false, NOT_CONFIGURED, 0, allowed, null, null);
        }
        long used = attempts.countByUserIdAndBankId(userId, bankId);
        Long open = attempts.findFirstByUserIdAndBankIdAndSubmittedAtIsNullOrderByIdDesc(userId, bankId)
                .map(StudentAttempt::getId).orElse(null);
        var last = attempts.findFirstByUserIdAndBankIdAndSubmittedAtIsNotNullOrderBySubmittedAtDesc(userId, bankId)
                .map(a -> new ExamStatus.LastResult(a.getScore(), a.getMaxScore(), a.getSubmittedAt())).orElse(null);
        String reason = courses.findActive(userId, courseId, Instant.now(clock)).isEmpty() ? ENROLLMENT_REQUIRED
                : percent < required ? PROGRESS
                : open == null && used >= allowed ? ATTEMPTS_EXHAUSTED : null;
        return new ExamStatus(bankId, percent, required, reason == null, reason, used, allowed, open, last);
    }
}

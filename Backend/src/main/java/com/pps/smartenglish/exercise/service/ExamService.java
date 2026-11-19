package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.catalog.domain.ContentStatus;
import com.pps.smartenglish.catalog.domain.Course;
import com.pps.smartenglish.catalog.domain.Topic;
import com.pps.smartenglish.catalog.repository.CourseLevelRepository;
import com.pps.smartenglish.catalog.repository.CourseRepository;
import com.pps.smartenglish.catalog.repository.TopicRepository;
import com.pps.smartenglish.exercise.domain.QuestionBank;
import com.pps.smartenglish.exercise.domain.StudentAttempt;
import com.pps.smartenglish.exercise.dto.CourseTest;
import com.pps.smartenglish.exercise.dto.ExamStatus;
import com.pps.smartenglish.exercise.repository.QuestionBankRepository;
import com.pps.smartenglish.exercise.repository.StudentAttemptRepository;
import com.pps.smartenglish.lms.service.CourseProgressService;
import com.pps.smartenglish.lms.service.UnitUnlockService;
import com.pps.smartenglish.shared.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-EXAM-02, D-35, D-46, D-118: điều kiện làm bài kiểm tra của khóa và trạng thái cho giao diện. Hai loại:
 * thi cuối khóa (mở theo tỷ lệ học, số lần thi lại của khóa) và kiểm tra chương (mở khi unit đã mở theo học tuần tự,
 * số lần làm lại của chương).
 */
@Service
public class ExamService {

    static final String ENROLLMENT_REQUIRED = "ENROLLMENT_REQUIRED";
    static final String PROGRESS = "PROGRESS";
    static final String ATTEMPTS_EXHAUSTED = "ATTEMPTS_EXHAUSTED";
    static final String NOT_CONFIGURED = "NOT_CONFIGURED";
    static final String UNIT_LOCKED = "UNIT_LOCKED";

    /** Bài kiểm tra mà bộ câu hỏi đang làm: topicId null = thi cuối khóa. */
    private record Target(Course course, Topic topic, int retakes) {}

    private final CourseRepository courses;
    private final TopicRepository topics;
    private final CourseLevelRepository levels;
    private final QuestionBankRepository banks;
    private final StudentAttemptRepository attempts;
    private final CourseProgressService progress;
    private final UnitUnlockService unitUnlock;
    private final Clock clock;

    ExamService(CourseRepository courses, TopicRepository topics, CourseLevelRepository levels, QuestionBankRepository banks,
                StudentAttemptRepository attempts, CourseProgressService progress, UnitUnlockService unitUnlock, Clock clock) {
        this.courses = courses;
        this.topics = topics;
        this.levels = levels;
        this.banks = banks;
        this.attempts = attempts;
        this.progress = progress;
        this.unitUnlock = unitUnlock;
        this.clock = clock;
    }

    /**
     * Gọi trong transaction của AttemptService.start. Bộ EXAM không là bài kiểm tra của khóa nào thì bỏ qua (chỉ chịu quy tắc
     * truy cập chung). Khóa dòng user trước khi đếm lượt để hai start song song không vượt số lượt.
     */
    void guardStart(long userId, long bankId) {
        var target = targetOf(bankId);
        if (target == null) {
            return;
        }
        attempts.lockUser(userId);
        requireEligible(userId, target);
        var open = attempts.findFirstByUserIdAndBankIdAndSubmittedAtIsNullOrderByIdDesc(userId, bankId);
        if (open.isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "exam_in_progress", "Bạn đang có một lượt thi chưa nộp",
                    Map.of("attemptId", open.get().getId()), null);
        }
        long used = attempts.countByUserIdAndBankId(userId, bankId);
        int allowed = 1 + target.retakes();
        if (used >= allowed) {
            throw new ApiException(HttpStatus.CONFLICT, "exam_attempts_exhausted", "Đã hết số lần thi",
                    Map.of("used", used, "allowed", allowed), null);
        }
    }

    /** Đề chỉ lộ khi học viên đã bắt đầu một lượt chưa nộp, để không xem trước đề. */
    void guardView(long userId, long bankId) {
        var target = targetOf(bankId);
        if (target == null) {
            return;
        }
        requireEligible(userId, target);
        if (attempts.findFirstByUserIdAndBankIdAndSubmittedAtIsNullOrderByIdDesc(userId, bankId).isEmpty()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "exam_not_started", "Hãy bắt đầu lượt thi trước khi xem đề");
        }
    }

    private Target targetOf(long bankId) {
        var course = courses.findByExamBankId(bankId).orElse(null);
        if (course != null) {
            return new Target(course, null, course.getExamRetakes());
        }
        return topics.findByChapterTestBankId(bankId)
                .flatMap(t -> levels.findById(t.getLevelId()).flatMap(lv -> courses.findById(lv.getCourseId()))
                        .map(c -> new Target(c, t, t.getChapterTestRetakes())))
                .orElse(null);
    }

    private void requireEligible(long userId, Target target) {
        Course course = target.course();
        if (courses.findActive(userId, course.getId(), Instant.now(clock)).isEmpty()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "exam_requires_enrollment", "Cần mua khóa học để làm bài thi");
        }
        if (target.topic() != null) {
            unitUnlock.requireUnlocked(userId, target.topic().getId());
            return;
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
        Course course = publishedCourse(courseId);
        int required = course.getExamUnlockPercent();
        int allowed = 1 + course.getExamRetakes();
        int percent = progress.compute(userId, courseId).percent();
        Long bankId = course.getExamBankId();
        if (bankId == null) {
            return new ExamStatus(null, percent, required, false, NOT_CONFIGURED, 0, allowed, null, null);
        }
        long used = attempts.countByUserIdAndBankId(userId, bankId);
        Long open = openAttemptId(userId, bankId);
        String reason = !enrolled(userId, courseId) ? ENROLLMENT_REQUIRED
                : percent < required ? PROGRESS
                : open == null && used >= allowed ? ATTEMPTS_EXHAUSTED : null;
        return new ExamStatus(bankId, percent, required, reason == null, reason, used, allowed, open, lastResult(userId, bankId));
    }

    /** Mọi bài kiểm tra của khóa (D-118): kiểm tra chương theo thứ tự học rồi thi cuối khóa. */
    @Transactional(readOnly = true)
    public List<CourseTest> tests(long userId, long courseId) {
        Course course = publishedCourse(courseId);
        boolean enrolled = enrolled(userId, courseId);
        var chapterTopics = topics.findPublishedWithChapterTest(courseId);
        var locked = unitUnlock.lockedTopicIds(userId, courseId, course.isSequential());
        List<CourseTest> out = new ArrayList<>();
        for (Topic t : chapterTopics) {
            long bankId = t.getChapterTestBankId();
            long used = attempts.countByUserIdAndBankId(userId, bankId);
            int allowed = 1 + t.getChapterTestRetakes();
            Long open = openAttemptId(userId, bankId);
            String reason = !enrolled ? ENROLLMENT_REQUIRED
                    : locked.contains(t.getId()) ? UNIT_LOCKED
                    : open == null && used >= allowed ? ATTEMPTS_EXHAUSTED : null;
            String title = banks.findById(bankId).map(QuestionBank::getTitle).orElse(t.getTitle());
            out.add(new CourseTest("CHAPTER", t.getId(), title, bankId, reason == null, reason, used, allowed, open,
                    lastResult(userId, bankId)));
        }
        var fin = status(userId, courseId);
        out.add(new CourseTest("FINAL", null, "Kiểm tra cuối khóa", fin.bankId(), fin.unlocked(), fin.reason(), fin.attemptsUsed(),
                fin.attemptsAllowed(), fin.inProgressAttemptId(), fin.lastResult()));
        return out;
    }

    private Course publishedCourse(long courseId) {
        return courses.findById(courseId).filter(c -> ContentStatus.PUBLISHED.equals(c.getStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "course_not_found", "Không tìm thấy khóa học"));
    }

    private boolean enrolled(long userId, long courseId) {
        return courses.findActive(userId, courseId, Instant.now(clock)).isPresent();
    }

    private Long openAttemptId(long userId, long bankId) {
        return attempts.findFirstByUserIdAndBankIdAndSubmittedAtIsNullOrderByIdDesc(userId, bankId).map(StudentAttempt::getId).orElse(null);
    }

    private ExamStatus.LastResult lastResult(long userId, long bankId) {
        return attempts.findFirstByUserIdAndBankIdAndSubmittedAtIsNotNullOrderBySubmittedAtDesc(userId, bankId)
                .map(a -> new ExamStatus.LastResult(a.getScore(), a.getMaxScore(), a.getSubmittedAt())).orElse(null);
    }
}

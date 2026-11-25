package com.pps.smartenglish.lms.service;

import com.pps.smartenglish.catalog.domain.Topic;
import com.pps.smartenglish.catalog.repository.CourseRepository;
import com.pps.smartenglish.catalog.repository.TopicRepository;
import com.pps.smartenglish.lms.dto.UnitGate;
import com.pps.smartenglish.lms.dto.UnitGate.ChapterTest;
import com.pps.smartenglish.lms.dto.UnitGate.Exercises;
import com.pps.smartenglish.lms.dto.UnitGate.Lectures;
import com.pps.smartenglish.lms.repository.UnitGateRepository;
import com.pps.smartenglish.lms.repository.UnitProgressRepository;
import com.pps.smartenglish.shared.error.ApiException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-LMS-04, D-122, D-124: giải thích vì sao một unit bị khóa. Luật mở unit nằm ở UnitUnlockService, không lặp lại ở đây. */
@Service
public class UnitGateService {

    private final CourseRepository courses;
    private final TopicRepository topics;
    private final UnitProgressRepository units;
    private final UnitGateRepository gate;
    private final UnitUnlockService unlock;
    private final EarlyUnlocks early;

    UnitGateService(CourseRepository courses, TopicRepository topics, UnitProgressRepository units, UnitGateRepository gate,
                    UnitUnlockService unlock, EarlyUnlocks early) {
        this.courses = courses;
        this.topics = topics;
        this.units = units;
        this.gate = gate;
        this.unlock = unlock;
        this.early = early;
    }

    @Transactional(readOnly = true)
    public UnitGate of(long userId, long courseId, long topicId) {
        var course = courses.findById(courseId).filter(c -> "PUBLISHED".equals(c.getStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "course_not_found", "Không tìm thấy khóa học"));
        List<Long> ordered = units.orderedTopicIds(courseId); // chỉ topic PUBLISHED của khóa này
        int index = ordered.indexOf(topicId);
        if (index < 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "unit_not_found", "Không tìm thấy unit");
        }
        Optional<Long> blocker = unlock.blockingTopic(userId, topicId);
        if (blocker.isEmpty()) {
            boolean early = course.isSequential() && openedEarly(userId, courseId, ordered, index);
            return new UnitGate(topicId, false, early, null, null, null, null, null);
        }
        long id = blocker.get();
        String title = topics.findById(id).map(Topic::getTitle).orElse(null);
        return new UnitGate(topicId, true, false, id, title, lectures(userId, id, course.getLessonCompletePercent()),
                exercises(userId, id), chapterTest(userId, id));
    }

    /** Lẽ ra khóa (không phải unit đầu, không học thử, có unit trước chưa xong) nhưng giáo viên đã mở sớm. */
    private boolean openedEarly(long userId, long courseId, List<Long> ordered, int index) {
        long topicId = ordered.get(index);
        if (index == 0 || !early.openedTopics(userId, courseId).contains(topicId)
                || topics.findById(topicId).map(Topic::isFreeTrial).orElse(true)) {
            return false;
        }
        Set<Long> unfinished = new HashSet<>(units.unfinishedTopicIds(courseId, userId));
        return ordered.subList(0, index).stream().anyMatch(unfinished::contains);
    }

    private Lectures lectures(long userId, long topicId, int required) {
        Object[] c = gate.lectureCounts(topicId, userId).get(0);
        int total = num(c[0]);
        if (total == 0) {
            return null;
        }
        int current = 0;
        var cur = gate.firstUnfinishedLesson(topicId, userId);
        if (!cur.isEmpty() && num(cur.get(0)[1]) > 0) {
            current = Math.max(0, Math.min(100, (int) ((long) num(cur.get(0)[0]) * 100 / num(cur.get(0)[1]))));
        }
        return new Lectures(num(c[1]), total, required, current);
    }

    private Exercises exercises(long userId, long topicId) {
        Object[] c = gate.exerciseCounts(topicId, userId).get(0);
        return num(c[0]) == 0 ? null : new Exercises(num(c[1]), num(c[0]));
    }

    private ChapterTest chapterTest(long userId, long topicId) {
        var rows = gate.chapterTest(topicId, userId);
        return rows.isEmpty() || rows.get(0)[0] == null ? null : new ChapterTest(num(rows.get(0)[1]) == 1);
    }

    private static int num(Object o) {
        return o == null ? 0 : ((Number) o).intValue();
    }
}

package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.catalog.domain.Course;
import com.pps.smartenglish.commerce.service.EnrollmentAccess;
import com.pps.smartenglish.exercise.repository.ExtraPoolRepository;
import com.pps.smartenglish.exercise.repository.ExtraPoolRepository.TopicRow;
import com.pps.smartenglish.exercise.repository.ExtraPoolRepository.TopicStat;
import com.pps.smartenglish.lms.service.UnitUnlockService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * D-152: trạng thái từng Topic của khóa đối với một học viên (quyền, hoàn thành, khóa tuần tự). Một chỗ cho cả màn tổng quan và
 * bắt đầu lượt, để hai nơi không bao giờ lệch nhau về Topic nào được làm.
 */
@Component
class ExtraTopics {

    enum Access { FULL, TRIAL, NONE }

    static final String COMPLETED = "COMPLETED";
    static final String OPEN = "OPEN";
    static final String PAID = "PAID";
    static final String LOCKED = "LOCKED";

    record State(TopicRow row, int levelNo, int topicNo, String state, Access access, TopicStat stat, Long blockedBy, String blockedTitle) {}

    private final ExtraPoolRepository pool;
    private final EnrollmentAccess enrollments;
    private final UnitUnlockService units;

    ExtraTopics(ExtraPoolRepository pool, EnrollmentAccess enrollments, UnitUnlockService units) {
        this.pool = pool;
        this.enrollments = enrollments;
        this.units = units;
    }

    List<State> of(long userId, Course course) {
        long courseId = course.getId();
        var rows = pool.topics(courseId);
        if (rows.isEmpty()) {
            return List.of();
        }
        var unfinished = units.unfinishedTopicIds(userId, courseId);
        var stats = pool.stats(userId, rows.stream().map(TopicRow::topicId).toList());
        var locked = units.lockedTopicIds(userId, courseId, course.isSequential());
        Long blocker = units.position(userId, courseId).topicId(); // topic chưa xong đầu tiên chặn mọi topic khóa phía sau
        String blockerTitle = rows.stream().filter(r -> r.topicId() == (blocker == null ? -1 : blocker)).map(TopicRow::title).findFirst().orElse(null);
        Map<Long, Boolean> paid = new HashMap<>();
        Map<Long, Integer> levelNos = new HashMap<>();
        Map<Long, Integer> topicNos = new HashMap<>();
        List<State> out = new ArrayList<>();
        for (TopicRow r : rows) {
            int levelNo = levelNos.computeIfAbsent(r.levelId(), k -> levelNos.size() + 1);
            int topicNo = topicNos.merge(r.levelId(), 1, Integer::sum);
            boolean owns = paid.computeIfAbsent(r.levelId(), k -> enrollments.hasAccess(userId, k));
            Access access = owns ? Access.FULL : r.freeTrial() ? Access.TRIAL : Access.NONE;
            var stat = stats.get(r.topicId());
            boolean done = stat != null && !unfinished.contains(r.topicId());
            boolean blocked = locked.contains(r.topicId()) && !r.freeTrial(); // học thử không bị khóa tuần tự, như UnitUnlockService
            String state = access == Access.NONE ? PAID : done ? COMPLETED : blocked ? LOCKED : OPEN;
            out.add(new State(r, levelNo, topicNo, state, access, stat, blocked && state.equals(LOCKED) ? blocker : null,
                    blocked && state.equals(LOCKED) ? blockerTitle : null));
        }
        return out;
    }

    /** Dạng nào được làm theo quyền: học thử chỉ dạng tự chấm (D-152 mục 4). */
    static boolean allows(Access access, ExtraKind kind) {
        return access == Access.FULL || access == Access.TRIAL && kind == ExtraKind.AUTO;
    }

    static Map<Long, Access> accessByTopic(List<State> states) {
        return states.stream().collect(Collectors.toMap(s -> s.row().topicId(), State::access));
    }
}

package com.pps.smartenglish.lms.service;

import com.pps.smartenglish.lms.repository.UnitProgressRepository;
import com.pps.smartenglish.shared.error.ApiException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-LMS-04, D-119, D-122: học tuần tự. Khóa bật sequential thì topic (unit) chỉ mở khi MỌI topic đứng trước đã xong
 * (không nhảy cóc). Topic đầu và topic học thử luôn mở. Chỉ có chiều khóa thêm: gọi sau khi quyền truy cập thường (mua khóa,
 * học thử) đã cho phép. Mở sớm của giáo viên (FR-CLS-06) sẽ nối vào đây khi phân hệ lớp có.
 */
@Service
public class UnitUnlockService {

    public static final String UNIT_LOCKED = "unit_locked";

    private final UnitProgressRepository units;
    private final EarlyUnlocks early;

    UnitUnlockService(UnitProgressRepository units, EarlyUnlocks early) {
        this.units = units;
        this.early = early;
    }

    /** Vị trí học của học viên: index 0-based của unit đầu tiên chưa xong (= total nếu đã xong hết), topicId của unit đó. */
    public record Position(int index, int total, Long topicId) {}

    @Transactional(readOnly = true)
    public Position position(long userId, long courseId) {
        List<Long> ordered = units.orderedTopicIds(courseId);
        Set<Long> unfinished = new HashSet<>(units.unfinishedTopicIds(courseId, userId));
        for (int i = 0; i < ordered.size(); i++) {
            if (unfinished.contains(ordered.get(i))) {
                return new Position(i, ordered.size(), ordered.get(i));
            }
        }
        return new Position(ordered.size(), ordered.size(), null);
    }

    /** Topic bị khóa của khóa này đối với học viên; rỗng nếu khóa không bật tuần tự. */
    @Transactional(readOnly = true)
    public Set<Long> lockedTopicIds(long userId, long courseId, boolean sequential) {
        if (!sequential) {
            return Set.of();
        }
        List<Long> ordered = units.orderedTopicIds(courseId);
        Set<Long> unfinished = new HashSet<>(units.unfinishedTopicIds(courseId, userId));
        Set<Long> locked = new HashSet<>();
        boolean blocked = false;
        for (Long id : ordered) {
            if (blocked) {
                locked.add(id);
            }
            blocked |= unfinished.contains(id);
        }
        locked.removeAll(early.openedTopics(userId, courseId)); // giáo viên mở sớm (FR-CLS-06)
        return locked;
    }

    /** Topic đứng trước đầu tiên chưa xong và đang chặn topicId; rỗng nếu topicId mở. Khách (userId null) không bị chặn. */
    @Transactional(readOnly = true)
    public Optional<Long> blockingTopic(Long userId, Long topicId) {
        if (userId == null || topicId == null) {
            return Optional.empty();
        }
        var rows = units.courseOf(topicId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] r = rows.get(0);
        boolean sequential = Boolean.TRUE.equals(r[1]);
        boolean freeTrial = Boolean.TRUE.equals(r[2]);
        if (!sequential || freeTrial) {
            return Optional.empty();
        }
        long courseId = ((Number) r[0]).longValue();
        if (early.openedTopics(userId, courseId).contains(topicId)) {
            return Optional.empty();
        }
        Set<Long> unfinished = new HashSet<>(units.unfinishedTopicIds(courseId, userId));
        for (Long id : units.orderedTopicIds(courseId)) {
            if (id.equals(topicId)) {
                return Optional.empty();
            }
            if (unfinished.contains(id)) {
                return Optional.of(id);
            }
        }
        return Optional.empty();
    }

    /** 403 unit_locked kèm topic cần hoàn thành trước. */
    public void requireUnlocked(Long userId, Long topicId) {
        blockingTopic(userId, topicId).ifPresent(blocker -> {
            // courseId, topicId để web dẫn tới màn điều kiện mở unit
            long courseId = ((Number) units.courseOf(topicId).get(0)[0]).longValue();
            throw new ApiException(HttpStatus.FORBIDDEN, UNIT_LOCKED, "Hãy hoàn thành unit trước để mở unit này",
                    Map.of("blockingTopicId", blocker, "courseId", courseId, "topicId", topicId), null);
        });
    }
}

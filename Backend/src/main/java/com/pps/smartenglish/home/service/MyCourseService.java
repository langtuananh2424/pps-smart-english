package com.pps.smartenglish.home.service;

import com.pps.smartenglish.home.dto.ActivityRow;
import com.pps.smartenglish.home.dto.CourseRow;
import com.pps.smartenglish.home.dto.HomeDtos.MyCourse;
import com.pps.smartenglish.home.repository.HomeReadRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** GET /api/me/courses: khóa có enrollment (kể cả hết hạn), khóa học gần nhất đứng đầu = "khóa đang học". */
@Service
public class MyCourseService {

    private static final String ACTIVE = "ACTIVE";
    private static final String EXPIRED = "EXPIRED";

    private final HomeReadRepository reads;
    private final Clock clock;

    MyCourseService(HomeReadRepository reads, Clock clock) {
        this.reads = reads;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MyCourse> list(long userId) {
        List<CourseRow> rows = reads.myCourses(userId);
        if (rows.isEmpty()) {
            return List.of();
        }
        var ids = rows.stream().map(CourseRow::courseId).toList();
        Map<Long, Instant> last = new HashMap<>();
        for (ActivityRow r : reads.lastLessonActivity(userId, ids)) {
            last.merge(r.courseId(), r.at(), MyCourseService::later);
        }
        for (ActivityRow r : reads.lastAttemptActivity(userId, ids)) {
            last.merge(r.courseId(), r.at(), MyCourseService::later);
        }
        Instant now = Instant.now(clock);
        // sort ổn định: hòa (hoặc cùng null) giữ thứ tự id khóa từ truy vấn
        return rows.stream()
                .map(r -> new MyCourse(r.courseId(), r.slug(), r.title(), r.targetType(), statusOf(r, now), r.expiresAt(),
                        last.get(r.courseId())))
                .sorted(Comparator.comparing(MyCourse::lastActivityAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private static String statusOf(CourseRow r, Instant now) {
        boolean expired = !ACTIVE.equals(r.status()) || (r.expiresAt() != null && !r.expiresAt().isAfter(now));
        return expired ? EXPIRED : ACTIVE;
    }

    private static Instant later(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }
}

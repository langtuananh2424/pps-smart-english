package com.pps.smartenglish.home.service;

import com.pps.smartenglish.catalog.repository.CourseRepository;
import com.pps.smartenglish.exercise.domain.QuestionType;
import com.pps.smartenglish.exercise.service.ExamBands;
import com.pps.smartenglish.home.config.ExerciseStatsProperties;
import com.pps.smartenglish.home.dto.CourseRow;
import com.pps.smartenglish.home.dto.HomeDtos.Band;
import com.pps.smartenglish.home.dto.HomeDtos.ExerciseStats;
import com.pps.smartenglish.home.dto.HomeDtos.TypeStat;
import com.pps.smartenglish.home.dto.HomeDtos.Weakest;
import com.pps.smartenglish.home.dto.TypeStatRow;
import com.pps.smartenglish.home.repository.HomeReadRepository;
import com.pps.smartenglish.placement.repository.PlacementAttemptRepository;
import com.pps.smartenglish.shared.error.ApiException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GET /api/me/courses/{id}/exercise-stats (D-62, D-63): thống kê bài tập luyện của một khóa, tính theo yêu cầu bằng
 * truy vấn gộp. Chỉ người có enrollment (kể cả hết hạn) hoặc đã học thử khóa mới xem được.
 */
@Service
public class ExerciseStatsService {

    private final CourseRepository courses;
    private final HomeReadRepository reads;
    private final PlacementAttemptRepository placements;
    private final ExerciseStatsProperties props;
    private final ExamBands examBands;

    ExerciseStatsService(CourseRepository courses, HomeReadRepository reads, PlacementAttemptRepository placements,
                         ExerciseStatsProperties props, ExamBands examBands) {
        this.examBands = examBands;
        this.courses = courses;
        this.reads = reads;
        this.placements = placements;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public ExerciseStats of(long userId, long courseId) {
        courses.findById(courseId).filter(c -> "PUBLISHED".equals(c.getStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "course_not_found", "Không tìm thấy khóa học"));
        boolean access = Stream.concat(reads.myCourses(userId).stream(), reads.trialCourses(userId).stream())
                .map(CourseRow::courseId).anyMatch(id -> id == courseId);
        if (!access) {
            throw new ApiException(HttpStatus.FORBIDDEN, "course_access_denied", "Bạn chưa có quyền với khóa học này");
        }
        Map<String, TypeStatRow> done = new HashMap<>();
        reads.answerStats(userId, List.of(courseId)).forEach(r -> done.put(r.type(), r));
        List<TypeStat> byType = reads.publishedQuestionCounts(courseId).stream()
                .sorted(Comparator.comparing(r -> QuestionType.valueOf(r.type())))
                .map(r -> typeStat(r, done.get(r.type()))).toList();
        long answered = done.values().stream().mapToLong(TypeStatRow::total).sum();
        long correct = done.values().stream().mapToLong(TypeStatRow::correct).sum();
        int practiced = (int) done.values().stream().filter(r -> r.total() > 0).count();
        return new ExerciseStats((int) answered, (int) correct, percent(correct, answered), practiced, byType, weakest(byType),
                band(userId));
    }

    private static TypeStat typeStat(TypeStatRow questions, TypeStatRow done) {
        long answered = done == null ? 0 : done.total();
        long correct = done == null ? 0 : done.correct();
        return new TypeStat(QuestionType.valueOf(questions.type()), questions.total(), answered, correct, percent(correct, answered));
    }

    /** Dạng đủ số câu tối thiểu có tỉ lệ đúng thấp nhất; hòa thì dạng nhiều câu hơn. */
    private Weakest weakest(List<TypeStat> byType) {
        return byType.stream().filter(t -> t.answered() > 0 && t.answered() >= props.weakMinAnswered())
                .min(Comparator.comparingInt(TypeStat::accuracy).thenComparing(Comparator.comparingLong(TypeStat::answered).reversed()))
                .map(t -> new Weakest(t.type(), t.accuracy(), t.answered())).orElse(null);
    }

    /** D-63: band bài thi khóa gần nhất (EXAM) nếu đã thi, không thì band kiểm tra đầu vào gần nhất (PLACEMENT). */
    private Band band(long userId) {
        return examBands.latest(userId).map(l -> new Band(l.overall(), "EXAM", l.at())).orElseGet(() -> placementBand(userId));
    }

    private Band placementBand(long userId) {
        return placements.findFirstByUserIdAndSubmittedAtIsNotNullOrderBySubmittedAtDescIdDesc(userId)
                .filter(a -> a.getOverallBand() != null)
                .map(a -> new Band(a.getOverallBand(), "PLACEMENT", a.getSubmittedAt())).orElse(null);
    }

    private static Integer percent(long correct, long answered) {
        return answered == 0 ? null : (int) Math.round(correct * 100.0 / answered);
    }
}

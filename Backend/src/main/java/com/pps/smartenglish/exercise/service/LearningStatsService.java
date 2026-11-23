package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.config.LearningStatsProperties;
import com.pps.smartenglish.exercise.dto.LearningDtos.Breakdown;
import com.pps.smartenglish.exercise.dto.LearningDtos.Improvement;
import com.pps.smartenglish.exercise.dto.LearningDtos.Metric;
import com.pps.smartenglish.exercise.dto.LearningDtos.Profile;
import com.pps.smartenglish.exercise.dto.LearningDtos.WeekPoint;
import com.pps.smartenglish.exercise.repository.LearningStatsRepository;
import com.pps.smartenglish.exercise.repository.LearningStatsRepository.AttemptTotals;
import com.pps.smartenglish.exercise.repository.LearningStatsRepository.Dimension;
import com.pps.smartenglish.exercise.repository.LearningStatsRepository.Scope;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-EX-08, FR-EX-09, D-137: kết quả cuối bài và hồ sơ năng lực, tính khi truy vấn, không gọi AI. Người gọi chịu trách
 * nhiệm phân quyền (chủ lượt, chủ hồ sơ, hoặc nhân sự lớp qua ClassAccess).
 */
@Service
public class LearningStatsService {

    private static final String UNSPECIFIED = "UNSPECIFIED";

    private final LearningStatsRepository stats;
    private final LearningStatsProperties props;
    private final Clock clock;

    LearningStatsService(LearningStatsRepository stats, LearningStatsProperties props, Clock clock) {
        this.stats = stats;
        this.props = props;
        this.clock = clock;
    }

    /** attemptId phải là lượt đã nộp của userId (AttemptService đã kiểm). */
    @Transactional(readOnly = true)
    public Breakdown breakdown(long userId, long attemptId) {
        var scope = Scope.attempt(attemptId);
        var byType = stats.metrics(Dimension.TYPE, scope);
        long answered = byType.stream().mapToLong(Metric::answered).sum();
        long correct = byType.stream().mapToLong(Metric::correct).sum();
        long ungraded = byType.stream().mapToLong(Metric::ungraded).sum();
        return new Breakdown(correct, answered - correct, ungraded, stats.metrics(Dimension.SKILL, scope), byType,
                stats.metrics(Dimension.GRAMMAR, scope), stats.wrongCounts(userId, attemptId));
    }

    @Transactional(readOnly = true)
    public Profile profile(long userId) {
        var scope = Scope.user(userId);
        var bySkill = stats.metrics(Dimension.SKILL, scope);
        var byType = stats.metrics(Dimension.TYPE, scope);
        var byGrammar = stats.metrics(Dimension.GRAMMAR, scope);
        long answered = byType.stream().mapToLong(Metric::answered).sum();
        long correct = byType.stream().mapToLong(Metric::correct).sum();
        BigDecimal credit = byType.stream().map(Metric::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Profile(answered, correct, credit, percent(correct, answered), bySkill, byType, byGrammar,
                stats.metrics(Dimension.TOPIC, scope), weeklyTrend(userId), improvements(bySkill, byGrammar));
    }

    /** Thứ Hai đầu tuần theo múi giờ cấu hình; đủ trendWeeks tuần gần nhất, tuần trống là 0. */
    private List<WeekPoint> weeklyTrend(long userId) {
        var zone = props.zone();
        LocalDate thisWeek = LocalDate.now(clock.withZone(zone)).with(DayOfWeek.MONDAY);
        LocalDate first = thisWeek.minusWeeks(props.trendWeeks() - 1L);
        Map<LocalDate, long[]> weeks = new TreeMap<>();
        for (LocalDate w = first; !w.isAfter(thisWeek); w = w.plusWeeks(1)) {
            weeks.put(w, new long[2]);
        }
        for (AttemptTotals t : stats.attemptTotalsSince(userId, first.atStartOfDay(zone).toOffsetDateTime())) {
            long[] bucket = weeks.get(t.submittedAt().atZoneSameInstant(zone).toLocalDate().with(DayOfWeek.MONDAY));
            if (bucket != null) {
                bucket[0] += t.answered();
                bucket[1] += t.correct();
            }
        }
        List<WeekPoint> out = new ArrayList<>();
        weeks.forEach((w, b) -> out.add(new WeekPoint(w, b[0], b[1], percent(b[1], b[0]))));
        return out;
    }

    /** Kỹ năng hoặc yếu tố ngữ pháp có tỉ lệ đúng thấp nhất, đủ số câu tối thiểu; hòa thì nhiều câu hơn trước. */
    private List<Improvement> improvements(List<Metric> bySkill, List<Metric> byGrammar) {
        return Stream.concat(
                        bySkill.stream().filter(m -> !UNSPECIFIED.equals(m.key())).map(m -> Map.entry("SKILL", m)),
                        byGrammar.stream().map(m -> Map.entry("GRAMMAR", m)))
                .filter(e -> e.getValue().answered() >= Math.max(1, props.improveMinAnswered()))
                .sorted(Comparator.<Map.Entry<String, Metric>>comparingInt(e -> e.getValue().accuracy())
                        .thenComparing(e -> -e.getValue().answered()))
                .limit(props.improveLimit())
                .map(e -> new Improvement(e.getKey(), e.getValue().key(), e.getValue().label(), e.getValue().accuracy(),
                        e.getValue().answered()))
                .toList();
    }

    private static Integer percent(long correct, long answered) {
        return answered == 0 ? null : (int) Math.round(correct * 100.0 / answered);
    }
}

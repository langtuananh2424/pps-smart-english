package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.catalog.domain.Course;
import com.pps.smartenglish.catalog.repository.CourseRepository;
import com.pps.smartenglish.exercise.config.ExtraProperties;
import com.pps.smartenglish.exercise.dto.ExtraDtos.LevelView;
import com.pps.smartenglish.exercise.dto.ExtraDtos.Overview;
import com.pps.smartenglish.exercise.dto.ExtraDtos.TopicCard;
import com.pps.smartenglish.exercise.domain.QuestionBank;
import com.pps.smartenglish.exercise.domain.QuestionType;
import com.pps.smartenglish.exercise.domain.StudentAttempt;
import com.pps.smartenglish.exercise.repository.QuestionBankRepository;
import com.pps.smartenglish.exercise.repository.StudentAttemptRepository;
import com.pps.smartenglish.exercise.repository.ExtraPoolRepository;
import com.pps.smartenglish.exercise.repository.ExtraPoolRepository.CountRow;
import com.pps.smartenglish.shared.error.ApiException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-EX-07, D-152: màn Luyện thêm của một khóa (Ôn lại, Thử thách, lọc Level, số câu theo dạng). */
@Service
public class ExtraOverviewService {

    private final CourseRepository courses;
    private final ExtraTopics topics;
    private final ExtraPoolRepository pool;
    private final ExtraProperties props;
    private final QuestionBankRepository banks;
    private final StudentAttemptRepository attempts;

    ExtraOverviewService(CourseRepository courses, ExtraTopics topics, ExtraPoolRepository pool, ExtraProperties props,
                         QuestionBankRepository banks, StudentAttemptRepository attempts) {
        this.banks = banks;
        this.attempts = attempts;
        this.courses = courses;
        this.topics = topics;
        this.pool = pool;
        this.props = props;
    }

    static Course requireCourse(CourseRepository courses, long courseId) {
        return courses.findById(courseId).filter(c -> "PUBLISHED".equals(c.getStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "course_not_found", "Không tìm thấy khóa học"));
    }

    @Transactional(readOnly = true)
    public Overview of(long userId, long courseId) {
        var course = requireCourse(courses, courseId);
        var states = topics.of(userId, course);
        Map<Long, List<CountRow>> counts = pool.counts(userId, courseId, ExtraKind.allTypes()).stream()
                .collect(Collectors.groupingBy(CountRow::topicId));
        Map<Long, LevelView> levels = new LinkedHashMap<>();
        List<TopicCard> review = new ArrayList<>();
        List<TopicCard> challenge = new ArrayList<>();
        for (var s : states) {
            var r = s.row();
            levels.putIfAbsent(r.levelId(), new LevelView(r.levelId(), r.levelTitle()));
            boolean usable = s.state().equals(ExtraTopics.COMPLETED) || s.state().equals(ExtraTopics.OPEN);
            var stat = s.stat();
            Integer accuracy = stat == null || stat.graded() == 0 ? null : (int) Math.round(stat.correct() * 100.0 / stat.graded());
            var card = new TopicCard(r.topicId(), r.title(), r.levelId(), r.levelTitle(), s.levelNo(), s.topicNo(), s.state(), accuracy,
                    stat == null ? null : stat.questions(), usable ? available(counts.getOrDefault(r.topicId(), List.of()), s) : null,
                    s.access() == ExtraTopics.Access.TRIAL, s.blockedBy(), s.blockedTitle());
            (s.state().equals(ExtraTopics.COMPLETED) ? review : challenge).add(card);
        }
        Collections.reverse(review); // mới học xong đứng đầu
        return new Overview(courseId, course.getSlug(), course.getTitle(), props.reviewSize(), props.challengeSize(), props.challengeMaxRatio(),
                List.copyOf(levels.values()), review, challenge, openAttemptId(userId));
    }

    private Long openAttemptId(long userId) {
        return banks.findByPurposeOrderByIdAsc(QuestionBank.EXTRA).stream().findFirst()
                .flatMap(b -> attempts.findFirstByUserIdAndBankIdAndSubmittedAtIsNullOrderByIdDesc(userId, b.getId()))
                .map(StudentAttempt::getId).orElse(null);
    }

    private static Map<String, Integer> available(List<CountRow> rows, ExtraTopics.State s) {
        var out = new EnumMap<ExtraKind, Integer>(ExtraKind.class);
        for (ExtraKind k : ExtraKind.values()) {
            out.put(k, 0);
        }
        for (CountRow c : rows) {
            var kind = ExtraKind.of(QuestionType.valueOf(c.type()));
            if (ExtraTopics.allows(s.access(), kind)) {
                out.merge(kind, c.n(), Integer::sum);
            }
        }
        Map<String, Integer> named = new LinkedHashMap<>();
        out.forEach((k, v) -> named.put(k.name(), v));
        return named;
    }
}

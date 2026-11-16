package com.pps.smartenglish.catalog.service;

import com.pps.smartenglish.catalog.domain.Course;
import com.pps.smartenglish.catalog.domain.CourseLevel;
import com.pps.smartenglish.catalog.domain.Lesson;
import com.pps.smartenglish.catalog.domain.Topic;
import com.pps.smartenglish.catalog.dto.CourseDetail;
import com.pps.smartenglish.catalog.dto.CourseDetail.LessonNode;
import com.pps.smartenglish.catalog.dto.CourseDetail.LevelNode;
import com.pps.smartenglish.catalog.dto.CourseDetail.TopicNode;
import com.pps.smartenglish.catalog.dto.CourseSummary;
import com.pps.smartenglish.catalog.repository.CourseLevelRepository;
import com.pps.smartenglish.catalog.repository.CourseRepository;
import com.pps.smartenglish.catalog.repository.LessonRepository;
import com.pps.smartenglish.catalog.repository.TopicRepository;
import com.pps.smartenglish.catalog.service.CourseStatsService.Stats;
import com.pps.smartenglish.shared.error.ApiException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

    private static final String PUBLISHED = "PUBLISHED";

    private final CourseRepository courses;
    private final CourseLevelRepository levels;
    private final TopicRepository topics;
    private final LessonRepository lessons;
    private final CourseStatsService stats;

    CatalogService(CourseRepository courses, CourseLevelRepository levels, TopicRepository topics, LessonRepository lessons,
                   CourseStatsService stats) {
        this.stats = stats;
        this.courses = courses;
        this.levels = levels;
        this.topics = topics;
        this.lessons = lessons;
    }

    /** Sắp theo bandFrom (khóa chưa có dải band xuống cuối) rồi id; số liệu thẻ lấy gộp, không N+1. */
    @Transactional(readOnly = true)
    public List<CourseSummary> listCourses() {
        List<Course> list = courses.findByStatusOrderByIdAsc(PUBLISHED).stream()
                .sorted(Comparator.comparing(Course::getBandFrom, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Course::getId))
                .toList();
        var all = stats.of(list.stream().map(Course::getId).toList());
        return list.stream().map(c -> summary(c, CourseStatsService.of(c.getId(), all))).toList();
    }

    private static CourseSummary summary(Course c, Stats s) {
        return new CourseSummary(c.getSlug(), c.getTitle(), c.getDescription(), c.getTargetType(), c.getBandFrom(),
                c.getBandTo(), s.trialLessonCount(), s.priceFromVnd(), s.levelCount(), s.lessonCount());
    }

    /** Dựng cây bằng 4 truy vấn cố định (course, levels, topics, lessons), không N+1. */
    @Transactional(readOnly = true)
    public CourseDetail detail(String slug) {
        var course = courses.findBySlugAndStatus(slug, PUBLISHED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "course_not_found", "Không tìm thấy khóa học"));
        // chỉ hiện level, topic, bài PUBLISHED; cha DRAFT thì con không có trong cây dù con đã PUBLISHED
        List<CourseLevel> lvls = levels.findByCourseIdAndStatusOrderBySortOrderAscIdAsc(course.getId(), PUBLISHED);
        List<Topic> tps = lvls.isEmpty() ? List.of()
                : topics.findByLevelIdInAndStatusOrderBySortOrderAscIdAsc(lvls.stream().map(CourseLevel::getId).toList(), PUBLISHED);
        List<Lesson> lss = tps.isEmpty() ? List.of()
                : lessons.findByTopicIdInAndStatusOrderBySortOrderAscIdAsc(tps.stream().map(Topic::getId).toList(), PUBLISHED);

        // groupingBy giữ thứ tự gặp trong danh sách đã sắp xếp từ DB
        Map<Long, List<LessonNode>> lessonsByTopic = lss.stream().collect(Collectors.groupingBy(Lesson::getTopicId,
                Collectors.mapping(l -> new LessonNode(l.getId(), l.getTitle(), l.getType(), l.getDurationSec()), Collectors.toList())));
        Map<Long, List<TopicNode>> topicsByLevel = tps.stream().collect(Collectors.groupingBy(Topic::getLevelId,
                Collectors.mapping(t -> new TopicNode(t.getId(), t.getTitle(), t.isFreeTrial(),
                        lessonsByTopic.getOrDefault(t.getId(), List.of())), Collectors.toList())));
        var tree = lvls.stream()
                .map(l -> new LevelNode(l.getId(), l.getCode(), l.getTitle(), topicsByLevel.getOrDefault(l.getId(), List.of())))
                .toList();
        Stats s = CourseStatsService.of(course.getId(), stats.of(List.of(course.getId())));
        return new CourseDetail(course.getSlug(), course.getTitle(), course.getDescription(), course.getTargetType(),
                course.getBandFrom(), course.getBandTo(), s.trialLessonCount(), s.priceFromVnd(), s.levelCount(),
                s.lessonCount(), s.products(), tree);
    }
}

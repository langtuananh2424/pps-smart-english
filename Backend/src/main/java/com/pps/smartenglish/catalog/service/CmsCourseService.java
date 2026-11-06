package com.pps.smartenglish.catalog.service;

import com.pps.smartenglish.catalog.domain.ContentStatus;
import com.pps.smartenglish.catalog.domain.Course;
import com.pps.smartenglish.catalog.domain.CourseLevel;
import com.pps.smartenglish.catalog.domain.Lesson;
import com.pps.smartenglish.catalog.domain.Topic;
import com.pps.smartenglish.catalog.dto.CmsDtos.CourseTree;
import com.pps.smartenglish.catalog.dto.CmsDtos.CourseView;
import com.pps.smartenglish.catalog.dto.CmsDtos.CreateCourse;
import com.pps.smartenglish.catalog.dto.CmsDtos.LevelNode;
import com.pps.smartenglish.catalog.dto.CmsDtos.PatchCourse;
import com.pps.smartenglish.catalog.dto.CmsDtos.TopicNode;
import com.pps.smartenglish.catalog.repository.CourseLevelRepository;
import com.pps.smartenglish.catalog.repository.CourseRepository;
import com.pps.smartenglish.catalog.repository.LessonRepository;
import com.pps.smartenglish.catalog.repository.TopicRepository;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.persistence.CmsWrites;
import com.pps.smartenglish.shared.web.CursorPage;
import com.pps.smartenglish.shared.web.Cursors;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CMS cho Course (FR-ADM-01). Chạy cho ADMIN và EDITOR. */
@Service
public class CmsCourseService {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;
    private static final String SLUG_TAKEN = "slug_taken";

    private final CourseRepository courses;
    private final CourseLevelRepository levels;
    private final TopicRepository topics;
    private final LessonRepository lessons;
    private final CmsWrites writes;

    CmsCourseService(CourseRepository courses, CourseLevelRepository levels, TopicRepository topics, LessonRepository lessons,
                     CmsWrites writes) {
        this.courses = courses;
        this.levels = levels;
        this.topics = topics;
        this.lessons = lessons;
        this.writes = writes;
    }

    @Transactional(readOnly = true)
    public CursorPage<CourseView> list(String limit, String cursor) {
        int size = Cursors.size(limit, DEFAULT_LIMIT, MAX_LIMIT);
        List<Course> rows = courses.findPage(Cursors.decode(cursor), PageRequest.of(0, size + 1));
        boolean more = rows.size() > size;
        List<Course> page = more ? rows.subList(0, size) : rows;
        return new CursorPage<>(page.stream().map(CmsCourseService::view).toList(),
                more ? Cursors.encode(page.get(size - 1).getId()) : null);
    }

    @Transactional
    public CourseView create(CreateCourse req) {
        if (courses.existsBySlug(req.slug())) {
            throw taken();
        }
        var course = writes.save(courses, new Course(req.slug(), req.title().strip(), req.description(), req.targetType()),
                SLUG_TAKEN, "Slug đã được dùng");
        return view(course);
    }

    /** Cây đầy đủ kể cả DRAFT, dựng bằng 4 truy vấn cố định. */
    @Transactional(readOnly = true)
    public CourseTree tree(long id) {
        Course course = find(id);
        List<CourseLevel> lvls = levels.findByCourseIdOrderBySortOrderAscIdAsc(id);
        List<Topic> tps = lvls.isEmpty() ? List.of()
                : topics.findByLevelIdInOrderBySortOrderAscIdAsc(lvls.stream().map(CourseLevel::getId).toList());
        List<Lesson> lss = tps.isEmpty() ? List.of()
                : lessons.findByTopicIdInOrderBySortOrderAscIdAsc(tps.stream().map(Topic::getId).toList());

        Map<Long, List<com.pps.smartenglish.catalog.dto.CmsDtos.LessonView>> lessonsByTopic = lss.stream()
                .collect(Collectors.groupingBy(Lesson::getTopicId, Collectors.mapping(CmsLessonService::view, Collectors.toList())));
        Map<Long, List<TopicNode>> topicsByLevel = tps.stream().collect(Collectors.groupingBy(Topic::getLevelId,
                Collectors.mapping(t -> new TopicNode(CmsTopicService.view(t), lessonsByTopic.getOrDefault(t.getId(), List.of())),
                        Collectors.toList())));
        return new CourseTree(view(course), lvls.stream()
                .map(l -> new LevelNode(CmsLevelService.view(l), topicsByLevel.getOrDefault(l.getId(), List.of()))).toList());
    }

    /** Trường vắng mặt (null) giữ nguyên. */
    @Transactional
    public CourseView patch(long id, PatchCourse req) {
        Course course = find(id);
        if (req.slug() != null) {
            if (courses.existsBySlugAndIdNot(req.slug(), id)) {
                throw taken();
            }
            course.setSlug(req.slug());
        }
        if (req.title() != null) {
            course.setTitle(req.title().strip());
        }
        if (req.description() != null) {
            course.setDescription(req.description());
        }
        if (req.targetType() != null) {
            course.setTargetType(req.targetType());
        }
        return view(writes.save(courses, course, SLUG_TAKEN, "Slug đã được dùng"));
    }

    /** Còn level thì in_use ngay; các tham chiếu khác (nếu có) do khóa ngoại chặn. */
    @Transactional
    public void delete(long id) {
        Course course = find(id);
        if (levels.existsByCourseId(id)) {
            throw CmsWrites.inUse();
        }
        writes.delete(courses, course);
    }

    /** Idempotent. Xuất bản khóa không tự xuất bản level con. */
    @Transactional
    public CourseView setPublished(long id, boolean published) {
        Course course = find(id);
        course.setStatus(published ? ContentStatus.PUBLISHED : ContentStatus.DRAFT);
        return view(writes.save(courses, course, SLUG_TAKEN, "Slug đã được dùng"));
    }

    private Course find(long id) {
        return courses.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "course_not_found", "Không tìm thấy khóa học"));
    }

    private static ApiException taken() {
        return new ApiException(HttpStatus.CONFLICT, SLUG_TAKEN, "Slug đã được dùng");
    }

    static CourseView view(Course c) {
        return new CourseView(c.getId(), c.getSlug(), c.getTitle(), c.getDescription(), c.getTargetType(), c.getStatus());
    }
}

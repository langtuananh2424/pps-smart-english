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
import com.pps.smartenglish.classroom.repository.ClassRepository;
import com.pps.smartenglish.catalog.repository.LessonRepository;
import com.pps.smartenglish.catalog.repository.TopicRepository;
import com.pps.smartenglish.exercise.domain.QuestionBank;
import com.pps.smartenglish.exercise.repository.QuestionBankRepository;
import com.pps.smartenglish.media.config.MediaProperties;
import com.pps.smartenglish.shared.audit.AuditRecorder;
import com.pps.smartenglish.shared.storage.ObjectStorage;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.persistence.CmsWrites;
import com.pps.smartenglish.shared.web.CursorPage;
import com.pps.smartenglish.shared.web.Cursors;
import java.math.BigDecimal;
import com.pps.smartenglish.lessoncontent.LessonSubtitleService;
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
    private final QuestionBankRepository banks;
    private final CmsWrites writes;
    private final ClassRepository classRooms;
    private final AuditRecorder audit;
    private final LessonSubtitleService subtitles;
    private final ObjectStorage storage;
    private final MediaProperties media;

    CmsCourseService(CourseRepository courses, CourseLevelRepository levels, TopicRepository topics, LessonRepository lessons,
                     QuestionBankRepository banks, CmsWrites writes, ClassRepository classRooms,
                     AuditRecorder audit, LessonSubtitleService subtitles, ObjectStorage storage, MediaProperties media) {
        this.storage = storage;
        this.media = media;
        this.subtitles = subtitles;
        this.courses = courses;
        this.levels = levels;
        this.topics = topics;
        this.lessons = lessons;
        this.banks = banks;
        this.writes = writes;
        this.classRooms = classRooms;
        this.audit = audit;
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
        var after = view(course);
        audit.record("CREATE", "COURSE", course.getId(), null, after);
        return after;
    }

    /** Cây đầy đủ kể cả DRAFT, dựng bằng 5 truy vấn cố định (gồm phụ đề của các bài; ký URL xem thử là tính cục bộ). */
    @Transactional(readOnly = true)
    public CourseTree tree(long id) {
        Course course = find(id);
        List<CourseLevel> lvls = levels.findByCourseIdOrderBySortOrderAscIdAsc(id);
        List<Topic> tps = lvls.isEmpty() ? List.of()
                : topics.findByLevelIdInOrderBySortOrderAscIdAsc(lvls.stream().map(CourseLevel::getId).toList());
        List<Lesson> lss = tps.isEmpty() ? List.of()
                : lessons.findByTopicIdInOrderBySortOrderAscIdAsc(tps.stream().map(Topic::getId).toList());

        var subs = subtitles.summaries(lss.stream().map(Lesson::getId).toList());
        Map<Long, List<com.pps.smartenglish.catalog.dto.CmsDtos.LessonView>> lessonsByTopic = lss.stream()
                .collect(Collectors.groupingBy(Lesson::getTopicId,
                        Collectors.mapping(l -> CmsLessonService.view(l, subs.getOrDefault(l.getId(), List.of()), CmsLessonService.videoUrl(l, storage, media)), Collectors.toList())));
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
        var before = view(course);
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
        if (req.aiPeriod() != null) {
            course.setAiPeriod(req.aiPeriod());
        }
        if (req.aiPeriodQuota() != null) {
            course.setAiPeriodQuota(req.aiPeriodQuota());
        }
        if (Boolean.TRUE.equals(req.clearExamBank())) {
            if (req.examBankId() != null) {
                throw ApiException.validation("clearExamBank", "Không dùng cùng examBankId");
            }
            course.setExamBankId(null);
        }
        if (req.examBankId() != null) {
            requireExamBankOf(id, req.examBankId());
            course.setExamBankId(req.examBankId());
        }
        if (req.deliveryMode() != null && !req.deliveryMode().equals(course.getDeliveryMode())) {
            if (Course.SELF_PACED.equals(req.deliveryMode()) && classRooms.existsByCourseId(course.getId())) {
                throw new ApiException(HttpStatus.CONFLICT, "course_has_classes", "Khóa đã có lớp, không đổi về khóa tự học được");
            }
            course.setDeliveryMode(req.deliveryMode());
        }
        if (req.ielts() != null) {
            course.setIelts(req.ielts());
        }
        if (req.sequential() != null) {
            course.setSequential(req.sequential());
        }
        if (req.lessonCompletePercent() != null) {
            course.setLessonCompletePercent(req.lessonCompletePercent());
        }
        if (req.examUnlockPercent() != null) {
            course.setExamUnlockPercent(req.examUnlockPercent());
        }
        if (req.examRetakes() != null) {
            course.setExamRetakes(req.examRetakes());
        }
        applyBand(course, req);
        var after = view(writes.save(courses, course, SLUG_TAKEN, "Slug đã được dùng"));
        audit.record("UPDATE", "COURSE", id, before, after);
        return after;
    }

    /** Còn level thì in_use ngay; các tham chiếu khác (nếu có) do khóa ngoại chặn. */
    @Transactional
    public void delete(long id) {
        Course course = find(id);
        if (levels.existsByCourseId(id)) {
            throw CmsWrites.inUse();
        }
        var before = view(course);
        writes.delete(courses, course);
        audit.record("DELETE", "COURSE", id, before, null);
    }

    /** Idempotent. Xuất bản khóa không tự xuất bản level con. */
    @Transactional
    public CourseView setPublished(long id, boolean published) {
        Course course = find(id);
        var before = view(course);
        course.setStatus(published ? ContentStatus.PUBLISHED : ContentStatus.DRAFT);
        var after = view(writes.save(courses, course, SLUG_TAKEN, "Slug đã được dùng"));
        audit.record(published ? "PUBLISH" : "UNPUBLISH", "COURSE", id, before, after);
        return after;
    }

    private Course find(long id) {
        return courses.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "course_not_found", "Không tìm thấy khóa học"));
    }

    /** Bài thi phải là bộ câu hỏi chế độ EXAM nằm trong chính khóa này. */
    private void requireExamBankOf(long courseId, long bankId) {
        boolean ok = banks.findById(bankId).filter(b -> QuestionBank.EXAM.equals(b.getMode())).isPresent()
                && banks.findCourseId(bankId).filter(c -> c == courseId).isPresent();
        if (!ok) {
            throw ApiException.validation("examBankId", "Phải là bộ câu hỏi chế độ EXAM thuộc khóa này");
        }
    }

    /** Hai đầu cùng có hoặc cùng không, bước 0.5, from < to (FR-GOAL-03). clearBand xóa dải; không dùng cùng bandFrom/bandTo. */
    private static void applyBand(Course course, PatchCourse req) {
        if (Boolean.TRUE.equals(req.clearBand())) {
            if (req.bandFrom() != null || req.bandTo() != null) {
                throw ApiException.validation("clearBand", "Không dùng cùng bandFrom/bandTo");
            }
            course.setBand(null, null);
            return;
        }
        if (req.bandFrom() == null && req.bandTo() == null) {
            return;
        }
        BigDecimal from = req.bandFrom() != null ? req.bandFrom() : course.getBandFrom();
        BigDecimal to = req.bandTo() != null ? req.bandTo() : course.getBandTo();
        if (from == null || to == null) {
            throw ApiException.validation("bandFrom", "Cần đủ cả bandFrom và bandTo");
        }
        if (from.compareTo(to) >= 0) {
            throw ApiException.validation("bandTo", "bandTo phải lớn hơn bandFrom");
        }
        if (from.multiply(BigDecimal.valueOf(2)).stripTrailingZeros().scale() > 0
                || to.multiply(BigDecimal.valueOf(2)).stripTrailingZeros().scale() > 0) {
            throw ApiException.validation("bandFrom", "Band tính theo bước 0.5");
        }
        course.setBand(from.setScale(1), to.setScale(1));
    }

    private static ApiException taken() {
        return new ApiException(HttpStatus.CONFLICT, SLUG_TAKEN, "Slug đã được dùng");
    }

    static CourseView view(Course c) {
        return new CourseView(c.getId(), c.getSlug(), c.getTitle(), c.getDescription(), c.getTargetType(), c.getStatus(),
                c.getAiPeriod(), c.getAiPeriodQuota(), c.getExamBankId(), c.getExamUnlockPercent(), c.getExamRetakes(),
                c.getBandFrom(), c.getBandTo(), c.isSequential(), c.getLessonCompletePercent(),
                c.getDeliveryMode(), c.isIelts());
    }
}

package com.pps.smartenglish.catalog.service;

import com.pps.smartenglish.catalog.domain.ContentStatus;
import com.pps.smartenglish.catalog.domain.CourseLevel;
import com.pps.smartenglish.catalog.domain.Topic;
import com.pps.smartenglish.catalog.dto.CmsDtos.CreateTopic;
import com.pps.smartenglish.catalog.dto.CmsDtos.PatchTopic;
import com.pps.smartenglish.catalog.dto.CmsDtos.TopicView;
import com.pps.smartenglish.catalog.repository.CourseLevelRepository;
import com.pps.smartenglish.catalog.repository.CourseRepository;
import com.pps.smartenglish.catalog.repository.LessonRepository;
import com.pps.smartenglish.catalog.repository.TopicRepository;
import com.pps.smartenglish.shared.audit.AuditRecorder;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.persistence.CmsWrites;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CMS cho Topic. level_id không đổi sau khi tạo. */
@Service
public class CmsTopicService {

    private static final String CONFLICT = "conflict";

    private final TopicRepository topics;
    private final CourseLevelRepository levels;
    private final CourseRepository courses;
    private final LessonRepository lessons;
    private final CmsWrites writes;
    private final AuditRecorder audit;

    CmsTopicService(TopicRepository topics, CourseLevelRepository levels, CourseRepository courses, LessonRepository lessons,
                    CmsWrites writes, AuditRecorder audit) {
        this.courses = courses;
        this.audit = audit;
        this.topics = topics;
        this.levels = levels;
        this.lessons = lessons;
        this.writes = writes;
    }

    @Transactional
    public TopicView create(long levelId, CreateTopic req) {
        var level = levels.findById(levelId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "level_not_found", "Không tìm thấy level"));
        // không nói rõ isFreeTrial: topic đầu của level đầu mà khóa chưa có học thử thì mặc định học thử (D-57)
        boolean free = req.isFreeTrial() != null ? req.isFreeTrial() : isFirstTopicOfFirstLevel(level);
        var topic = new Topic(levelId, req.title().strip(), req.sortOrder() == null ? 0 : req.sortOrder(), free);
        var saved = writes.save(topics, topic, CONFLICT, "Không lưu được topic");
        if (saved.isFreeTrial()) {
            keepSingleFreeTrial(saved);
        }
        var after = view(saved);
        audit.record("CREATE", "TOPIC", after.id(), null, after);
        return after;
    }

    @Transactional
    public TopicView patch(long id, PatchTopic req) {
        Topic topic = find(id);
        var before = view(topic);
        if (req.title() != null) {
            topic.setTitle(req.title().strip());
        }
        if (req.sortOrder() != null) {
            topic.setSortOrder(req.sortOrder());
        }
        if (req.isFreeTrial() != null) {
            topic.setFreeTrial(req.isFreeTrial());
            if (req.isFreeTrial()) {
                keepSingleFreeTrial(topic);
            }
        }
        var after = view(writes.save(topics, topic, CONFLICT, "Không lưu được topic"));
        audit.record("UPDATE", "TOPIC", id, before, after);
        return after;
    }

    /** Còn bài học hoặc bộ câu hỏi tham chiếu thì in_use. */
    @Transactional
    public void delete(long id) {
        Topic topic = find(id);
        if (lessons.existsByTopicId(id)) {
            throw CmsWrites.inUse();
        }
        audit.record("DELETE", "TOPIC", id, view(topic), null);
        writes.delete(topics, topic);
    }

    /** Idempotent. Xuất bản topic không tự xuất bản bài con. */
    @Transactional
    public TopicView setPublished(long id, boolean published) {
        Topic topic = find(id);
        var before = view(topic);
        topic.setStatus(published ? ContentStatus.PUBLISHED : ContentStatus.DRAFT);
        var after = view(writes.save(topics, topic, CONFLICT, "Không lưu được topic"));
        audit.record(published ? "PUBLISH" : "UNPUBLISH", "TOPIC", id, before, after);
        return after;
    }

    private boolean isFirstTopicOfFirstLevel(CourseLevel level) {
        courses.lockById(level.getCourseId());
        return !topics.existsByLevelId(level.getId())
                && levels.findByCourseIdOrderBySortOrderAscIdAsc(level.getCourseId()).get(0).getId().equals(level.getId())
                && topics.findFreeTrialByCourse(level.getCourseId()).isEmpty();
    }

    /**
     * D-57: mỗi khóa tối đa một topic học thử; bật topic này thì tắt các topic khác của khóa. Khóa dòng course trước
     * để hai lần bật đồng thời chạy tuần tự (topic không có course_id nên không có unique index chặn ở DB).
     */
    private void keepSingleFreeTrial(Topic topic) {
        Long courseId = levels.findById(topic.getLevelId()).orElseThrow().getCourseId();
        courses.lockById(courseId);
        for (Topic other : topics.findFreeTrialByCourse(courseId)) {
            if (!other.getId().equals(topic.getId())) {
                var before = view(other);
                other.setFreeTrial(false);
                audit.record("UPDATE", "TOPIC", other.getId(), before, view(other));
            }
        }
    }

    private Topic find(long id) {
        return topics.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "topic_not_found", "Không tìm thấy topic"));
    }

    static TopicView view(Topic t) {
        return new TopicView(t.getId(), t.getLevelId(), t.getTitle(), t.getSortOrder(), t.isFreeTrial(), t.getStatus());
    }
}

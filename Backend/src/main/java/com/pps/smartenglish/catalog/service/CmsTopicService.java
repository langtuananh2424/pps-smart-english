package com.pps.smartenglish.catalog.service;

import com.pps.smartenglish.catalog.domain.ContentStatus;
import com.pps.smartenglish.catalog.domain.Topic;
import com.pps.smartenglish.catalog.dto.CmsDtos.CreateTopic;
import com.pps.smartenglish.catalog.dto.CmsDtos.PatchTopic;
import com.pps.smartenglish.catalog.dto.CmsDtos.TopicView;
import com.pps.smartenglish.catalog.repository.CourseLevelRepository;
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
    private final LessonRepository lessons;
    private final CmsWrites writes;
    private final AuditRecorder audit;

    CmsTopicService(TopicRepository topics, CourseLevelRepository levels, LessonRepository lessons, CmsWrites writes,
                    AuditRecorder audit) {
        this.audit = audit;
        this.topics = topics;
        this.levels = levels;
        this.lessons = lessons;
        this.writes = writes;
    }

    @Transactional
    public TopicView create(long levelId, CreateTopic req) {
        if (!levels.existsById(levelId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "level_not_found", "Không tìm thấy level");
        }
        var topic = new Topic(levelId, req.title().strip(), req.sortOrder() == null ? 0 : req.sortOrder(),
                Boolean.TRUE.equals(req.isFreeTrial()));
        var after = view(writes.save(topics, topic, CONFLICT, "Không lưu được topic"));
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

    private Topic find(long id) {
        return topics.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "topic_not_found", "Không tìm thấy topic"));
    }

    static TopicView view(Topic t) {
        return new TopicView(t.getId(), t.getLevelId(), t.getTitle(), t.getSortOrder(), t.isFreeTrial(), t.getStatus());
    }
}

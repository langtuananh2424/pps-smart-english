package com.pps.smartenglish.catalog.service;

import com.pps.smartenglish.catalog.domain.ContentStatus;
import com.pps.smartenglish.catalog.domain.Lesson;
import com.pps.smartenglish.catalog.dto.CmsDtos.CreateLesson;
import com.pps.smartenglish.catalog.dto.CmsDtos.LessonView;
import com.pps.smartenglish.catalog.dto.CmsDtos.PatchLesson;
import com.pps.smartenglish.catalog.repository.LessonRepository;
import com.pps.smartenglish.catalog.repository.TopicRepository;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.persistence.CmsWrites;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CMS cho Lesson. topic_id không đổi sau khi tạo. Đợt này chỉ nhận video YOUTUBE; STORAGE (tải media lên) làm ở đợt sau.
 */
@Service
public class CmsLessonService {

    private static final String YOUTUBE = "YOUTUBE";
    private static final String CONFLICT = "conflict";

    private final LessonRepository lessons;
    private final TopicRepository topics;
    private final CmsWrites writes;

    CmsLessonService(LessonRepository lessons, TopicRepository topics, CmsWrites writes) {
        this.lessons = lessons;
        this.topics = topics;
        this.writes = writes;
    }

    @Transactional
    public LessonView create(long topicId, CreateLesson req) {
        if (!topics.existsById(topicId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "topic_not_found", "Không tìm thấy topic");
        }
        var lesson = new Lesson(topicId, req.type(), req.title().strip(), req.durationSec(),
                req.sortOrder() == null ? 0 : req.sortOrder());
        applyVideo(lesson, req.videoSource(), req.videoRef());
        return view(writes.save(lessons, lesson, CONFLICT, "Không lưu được bài học"));
    }

    @Transactional
    public LessonView patch(long id, PatchLesson req) {
        Lesson lesson = find(id);
        if (req.type() != null) {
            lesson.setType(req.type());
        }
        if (req.title() != null) {
            lesson.setTitle(req.title().strip());
        }
        if (req.durationSec() != null) {
            lesson.setDurationSec(req.durationSec());
        }
        if (req.sortOrder() != null) {
            lesson.setSortOrder(req.sortOrder());
        }
        applyVideo(lesson, req.videoSource(), req.videoRef());
        return view(writes.save(lessons, lesson, CONFLICT, "Không lưu được bài học"));
    }

    /** Còn tiến độ học hoặc bộ câu hỏi tham chiếu thì in_use (khóa ngoại). */
    @Transactional
    public void delete(long id) {
        writes.delete(lessons, find(id));
    }

    /** Idempotent. Xuất bản cần có video và thời lượng; hạ xuống DRAFT thì luôn được. */
    @Transactional
    public LessonView setPublished(long id, boolean published) {
        Lesson lesson = find(id);
        if (published) {
            if (lesson.getVideoRef() == null || lesson.getVideoRef().isBlank()) {
                throw ApiException.validation("videoRef", "Cần có video trước khi xuất bản");
            }
            if (lesson.getDurationSec() == null || lesson.getDurationSec() <= 0) {
                throw ApiException.validation("durationSec", "Cần có thời lượng lớn hơn 0 trước khi xuất bản");
            }
        }
        lesson.setStatus(published ? ContentStatus.PUBLISHED : ContentStatus.DRAFT);
        return view(writes.save(lessons, lesson, CONFLICT, "Không lưu được bài học"));
    }

    /** videoRef là mã hoặc URL YouTube, luôn lưu dạng mã. Có videoRef mà không nói nguồn thì mặc định YOUTUBE. */
    private static void applyVideo(Lesson lesson, String source, String ref) {
        if (source != null && !YOUTUBE.equals(source)) {
            throw ApiException.validation("videoSource", "Đợt này chỉ nhận YOUTUBE");
        }
        if (ref != null) {
            lesson.setVideo(YOUTUBE, YoutubeRef.normalize(ref));
        }
    }

    private Lesson find(long id) {
        return lessons.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "lesson_not_found", "Không tìm thấy bài học"));
    }

    static LessonView view(Lesson l) {
        return new LessonView(l.getId(), l.getTopicId(), l.getType(), l.getTitle(), l.getDurationSec(), l.getSortOrder(), l.getStatus(),
                l.getVideoSource(), l.getVideoRef());
    }
}

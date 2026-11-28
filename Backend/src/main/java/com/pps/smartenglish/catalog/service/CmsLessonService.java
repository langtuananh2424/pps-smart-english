package com.pps.smartenglish.catalog.service;

import com.pps.smartenglish.catalog.domain.ContentStatus;
import com.pps.smartenglish.catalog.domain.Lesson;
import com.pps.smartenglish.catalog.dto.CmsDtos.CreateLesson;
import com.pps.smartenglish.catalog.dto.CmsDtos.LessonView;
import com.pps.smartenglish.catalog.dto.CmsDtos.PatchLesson;
import com.pps.smartenglish.catalog.repository.LessonRepository;
import com.pps.smartenglish.catalog.repository.TopicRepository;
import com.pps.smartenglish.lessoncontent.LessonContentDtos.SubtitleInfo;
import com.pps.smartenglish.lessoncontent.LessonSubtitleService;
import com.pps.smartenglish.media.config.MediaProperties;
import com.pps.smartenglish.media.service.VideoMediaService;
import com.pps.smartenglish.shared.audit.AuditRecorder;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.persistence.CmsWrites;
import com.pps.smartenglish.shared.storage.ObjectStorage;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CMS cho Lesson. topic_id không đổi sau khi tạo. Video YOUTUBE (mã) hoặc STORAGE (asset VIDEO READY đã tải lên, D-171).
 */
@Service
public class CmsLessonService {

    private static final String YOUTUBE = "YOUTUBE";
    private static final String STORAGE = "STORAGE";
    private static final String CONFLICT = "conflict";

    private final LessonRepository lessons;
    private final TopicRepository topics;
    private final CmsWrites writes;
    private final AuditRecorder audit;
    private final VideoMediaService video;
    private final ObjectStorage storage;
    private final MediaProperties media;
    private final LessonSubtitleService subtitles;

    CmsLessonService(LessonRepository lessons, TopicRepository topics, CmsWrites writes, AuditRecorder audit,
                     VideoMediaService video, ObjectStorage storage, MediaProperties media, LessonSubtitleService subtitles) {
        this.video = video;
        this.storage = storage;
        this.media = media;
        this.subtitles = subtitles;
        this.audit = audit;
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
        applyVideo(lesson, req.videoSource(), req.videoRef(), req.videoMediaId());
        var saved = writes.save(lessons, lesson, CONFLICT, "Không lưu được bài học");
        audit.record("CREATE", "LESSON", saved.getId(), null, view(saved));
        return detail(saved);
    }

    @Transactional
    public LessonView patch(long id, PatchLesson req) {
        Lesson lesson = find(id);
        var before = view(lesson);
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
        applyVideo(lesson, req.videoSource(), req.videoRef(), req.videoMediaId());
        var saved = writes.save(lessons, lesson, CONFLICT, "Không lưu được bài học");
        audit.record("UPDATE", "LESSON", id, before, view(saved));
        return detail(saved);
    }

    /** Còn tiến độ học hoặc bộ câu hỏi tham chiếu thì in_use (khóa ngoại). */
    @Transactional
    public void delete(long id) {
        Lesson lesson = find(id);
        audit.record("DELETE", "LESSON", id, view(lesson), null);
        writes.delete(lessons, lesson);
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
            if (STORAGE.equals(lesson.getVideoSource()) && !video.isReadyVideoKey(lesson.getVideoRef())) {
                throw ApiException.validation("videoRef", "Video đã tải lên chưa sẵn sàng");
            }
        }
        var before = view(lesson);
        lesson.setStatus(published ? ContentStatus.PUBLISHED : ContentStatus.DRAFT);
        var saved = writes.save(lessons, lesson, CONFLICT, "Không lưu được bài học");
        audit.record(published ? "PUBLISH" : "UNPUBLISH", "LESSON", id, before, view(saved));
        return detail(saved);
    }

    /**
     * YOUTUBE: videoRef là mã hoặc URL, luôn lưu dạng mã; có videoRef mà không nói nguồn thì mặc định YOUTUBE.
     * STORAGE: videoMediaId là video READY, lưu khóa kho; thời lượng trống thì lấy từ file.
     * shortcut: file video cũ không xóa khi thay, thêm dọn file mồ côi khi ổ media chật.
     */
    private void applyVideo(Lesson lesson, String source, String ref, Long mediaId) {
        if (STORAGE.equals(source)) {
            if (mediaId == null) {
                throw ApiException.validation("videoMediaId", "Cần videoMediaId khi nguồn là STORAGE");
            }
            var asset = video.requireVideo(mediaId);
            lesson.setVideo(STORAGE, asset.getStorageKey());
            if (lesson.getDurationSec() == null && asset.getDurationMs() != null) {
                lesson.setDurationSec((asset.getDurationMs() + 999) / 1000);
            }
            return;
        }
        if (source != null && !YOUTUBE.equals(source)) {
            throw ApiException.validation("videoSource", "Chỉ nhận YOUTUBE hoặc STORAGE");
        }
        if (mediaId != null) {
            throw ApiException.validation("videoMediaId", "Chỉ dùng khi videoSource là STORAGE");
        }
        if (ref != null) {
            lesson.setVideo(YOUTUBE, YoutubeRef.normalize(ref));
        }
    }

    /** Phản hồi một bài: thêm phụ đề và link xem lại video STORAGE. Audit không dùng bản này (link ký không nên vào nhật ký). */
    private LessonView detail(Lesson l) {
        var subs = subtitles.summaries(List.of(l.getId())).getOrDefault(l.getId(), List.of());
        return view(l, subs, videoUrl(l, storage, media));
    }

    private Lesson find(long id) {
        return lessons.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "lesson_not_found", "Không tìm thấy bài học"));
    }

    /** Link xem thử video STORAGE; ký cục bộ, không gọi mạng. */
    static String videoUrl(Lesson l, ObjectStorage storage, MediaProperties media) {
        return STORAGE.equals(l.getVideoSource()) ? storage.presignGet(l.getVideoRef(), media.urlTtl()) : null;
    }

    static LessonView view(Lesson l) {
        return view(l, List.of(), null);
    }

    static LessonView view(Lesson l, List<SubtitleInfo> subs, String videoUrl) {
        return new LessonView(l.getId(), l.getTopicId(), l.getType(), l.getTitle(), l.getDurationSec(), l.getSortOrder(), l.getStatus(),
                l.getVideoSource(), l.getVideoRef(), videoUrl, subs);
    }
}

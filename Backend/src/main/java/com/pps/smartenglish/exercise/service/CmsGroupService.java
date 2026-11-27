package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.catalog.service.YoutubeRef;
import com.pps.smartenglish.exercise.content.LayoutRenderer;
import com.pps.smartenglish.exercise.content.RichText;
import com.pps.smartenglish.exercise.domain.QuestionGroup;
import com.pps.smartenglish.exercise.dto.QuestionGroupDtos.CreateGroup;
import com.pps.smartenglish.exercise.dto.QuestionGroupDtos.GroupView;
import com.pps.smartenglish.exercise.dto.QuestionGroupDtos.PatchGroup;
import com.pps.smartenglish.exercise.listening.AudioMediaService;
import com.pps.smartenglish.exercise.repository.QuestionBankRepository;
import com.pps.smartenglish.exercise.repository.QuestionGroupRepository;
import com.pps.smartenglish.exercise.repository.QuestionRepository;
import com.pps.smartenglish.exercise.validation.LayoutValidator;
import com.pps.smartenglish.media.service.VideoMediaService;
import com.pps.smartenglish.shared.audit.AuditRecorder;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.persistence.CmsWrites;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** D-133 (FR-ADM-02): nhóm câu hỏi dùng chung bài đọc. Chạy cho ADMIN và EDITOR. Nhóm còn câu hỏi thì không xóa được. */
@Service
public class CmsGroupService {

    private static final String AUDIT_TYPE = "QUESTION_GROUP";

    private final QuestionGroupRepository groups;
    private final QuestionBankRepository banks;
    private final QuestionRepository questions;
    private final RichText rich;
    private final CmsWrites writes;
    private final AuditRecorder audit;
    private final AudioMediaService audio;
    private final VideoMediaService video;
    private final LayoutValidator layoutValidator;
    private final GroupLayoutCheck layoutCheck;
    private final LayoutRenderer layoutRenderer;
    private final com.pps.smartenglish.exercise.config.VideoQuizProperties videoProps;

    CmsGroupService(QuestionGroupRepository groups, QuestionBankRepository banks, QuestionRepository questions, RichText rich,
                    CmsWrites writes, AuditRecorder audit, AudioMediaService audio, VideoMediaService video,
                    LayoutValidator layoutValidator, GroupLayoutCheck layoutCheck, LayoutRenderer layoutRenderer,
                    com.pps.smartenglish.exercise.config.VideoQuizProperties videoProps) {
        this.videoProps = videoProps;
        this.layoutValidator = layoutValidator;
        this.layoutCheck = layoutCheck;
        this.layoutRenderer = layoutRenderer;
        this.audio = audio;
        this.video = video;
        this.groups = groups;
        this.banks = banks;
        this.questions = questions;
        this.rich = rich;
        this.writes = writes;
        this.audit = audit;
    }

    @Transactional
    public GroupView create(long bankId, CreateGroup req) {
        if (!banks.existsById(bankId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "bank_not_found", "Không tìm thấy bộ câu hỏi");
        }
        var group = new QuestionGroup(bankId, req.sortOrder() == null ? 0 : req.sortOrder(), blankToNull(req.title()),
                blankToNull(req.instruction()), passage(req.passageHtml()));
        group.setAudioAssetId(audioId(req.audioAssetId()));
        group.setTranscriptHtml(passage(req.transcriptHtml()));
        group.setVideoAssetId(videoId(req.videoAssetId()));
        group.setVideoYoutubeId(youtubeId(req.videoYoutube()));
        group.setVideoDurationSec(group.getVideoYoutubeId() == null ? null : durationSec(req.videoDurationSec()));
        requireOneVideo(group);
        group.setLayout(layoutOf(req.layout()));
        var after = view(writes.save(groups, group, "conflict", "Không lưu được nhóm câu hỏi"));
        audit.record("CREATE", AUDIT_TYPE, after.id(), null, after);
        return after;
    }

    @Transactional
    public GroupView patch(long id, PatchGroup req) {
        QuestionGroup g = find(id);
        var before = view(g);
        if (req.title() != null) {
            g.setTitle(blankToNull(req.title()));
        }
        if (req.instruction() != null) {
            g.setInstruction(blankToNull(req.instruction()));
        }
        if (req.passageHtml() != null) {
            g.setPassageHtml(passage(req.passageHtml()));
        }
        if (req.audioAssetId() != null) {
            g.setAudioAssetId(req.audioAssetId().isNull() ? null : audioId(longOf(req.audioAssetId(), "audioAssetId")));
        }
        if (req.transcriptHtml() != null) {
            g.setTranscriptHtml(passage(req.transcriptHtml()));
        }
        if (req.videoAssetId() != null) {
            g.setVideoAssetId(req.videoAssetId().isNull() ? null : videoId(longOf(req.videoAssetId(), "videoAssetId")));
        }
        if (req.videoYoutube() != null) {
            g.setVideoYoutubeId(req.videoYoutube().isNull() ? null : youtubeId(req.videoYoutube().asText()));
        }
        if (req.videoDurationSec() != null) {
            g.setVideoDurationSec(req.videoDurationSec().isNull() ? null : durationSec(longOf(req.videoDurationSec(), "videoDurationSec").intValue()));
        }
        if (g.getVideoYoutubeId() == null) {
            g.setVideoDurationSec(null); // chỉ video YouTube có thời lượng nhập tay
        }
        requireOneVideo(g);
        if (req.layout() != null) {
            g.setLayout(layoutOf(req.layout()));
        }
        if (req.layout() != null || req.audioAssetId() != null || req.videoAssetId() != null || req.videoYoutube() != null
                || req.videoDurationSec() != null) {
            layoutCheck.checkGroup(g); // câu đang dùng nhóm vẫn phải khớp layout và audio mới
        }
        if (req.sortOrder() != null) {
            g.setSortOrder(req.sortOrder());
        }
        var after = view(writes.save(groups, g, "conflict", "Không lưu được nhóm câu hỏi"));
        audit.record("UPDATE", AUDIT_TYPE, id, before, after);
        return after;
    }

    /** Còn câu hỏi trong nhóm thì in_use; khóa ngoại là chốt cuối cho trường hợp tranh chấp. */
    @Transactional
    public void delete(long id) {
        QuestionGroup g = find(id);
        if (questions.existsByGroupId(id)) {
            throw CmsWrites.inUse();
        }
        audit.record("DELETE", AUDIT_TYPE, id, view(g), null);
        writes.delete(groups, g);
    }

    /** Nhóm của một bộ theo sortOrder; một lần dựng HTML cho cả danh sách. */
    @Transactional(readOnly = true)
    public List<GroupView> ofBank(long bankId) {
        return views(groups.findByBankIdOrderBySortOrderAscIdAsc(bankId));
    }

    /** null hoặc JSON null = không có layout; còn lại phải qua LayoutValidator. */
    private com.fasterxml.jackson.databind.JsonNode layoutOf(com.fasterxml.jackson.databind.JsonNode raw) {
        return raw == null || raw.isNull() ? null : layoutValidator.validate(raw);
    }

    /** Rỗng sau khi làm sạch (không chữ, không ảnh) = không có bài đọc. */
    private String passage(String html) {
        if (html == null) {
            return null;
        }
        var s = rich.sanitize(html);
        rich.requireReady(s.assetIds());
        return s.text().isEmpty() && s.assetIds().isEmpty() ? null : s.html();
    }

    /** Audio phải READY và đúng loại; null = không có audio. */
    private Long audioId(Long id) {
        return id == null ? null : audio.requireAudio(id).getId();
    }

    /** Video phải READY và đúng loại; null = không có video file. */
    private Long videoId(Long id) {
        return id == null ? null : video.requireVideo(id).getId();
    }

    /** Link/mã YouTube chuẩn hóa về mã 11 ký tự; rỗng = không có. */
    private static String youtubeId(String raw) {
        return raw == null || raw.isBlank() ? null : YoutubeRef.normalize(raw, "videoYoutube");
    }

    /** Thời lượng YouTube (giây) trong 1..maxDurationSec; null = chưa nhập. */
    private Integer durationSec(Integer sec) {
        if (sec != null && (sec < 1 || sec > videoProps.maxDurationSec())) {
            throw ApiException.validation("videoDurationSec", "Phải từ 1 đến " + videoProps.maxDurationSec() + " giây");
        }
        return sec;
    }

    /** Tối đa một nguồn video; kiểm trên trạng thái cuối nên đổi nguồn trong một PATCH vẫn được. */
    private static void requireOneVideo(QuestionGroup g) {
        if (g.getVideoAssetId() != null && g.getVideoYoutubeId() != null) {
            throw ApiException.validation("videoAssetId", "Chỉ chọn file tải lên hoặc link YouTube, không chọn cả hai");
        }
    }

    private static Long longOf(com.fasterxml.jackson.databind.JsonNode n, String field) {
        if (!n.isIntegralNumber() || n.asLong() <= 0) {
            throw ApiException.validation(field, "Phải là số nguyên dương hoặc null");
        }
        return n.asLong();
    }

    private GroupView view(QuestionGroup g) {
        return views(List.of(g)).get(0);
    }

    private List<GroupView> views(List<QuestionGroup> rows) {
        Map<String, String> html = rich.render(rows.stream().flatMap(g -> java.util.stream.Stream.of(g.getPassageHtml(), g.getTranscriptHtml())).toList());
        var layouts = layoutRenderer.render(rows);
        var durations = audio.durations(rows.stream().map(QuestionGroup::getAudioAssetId).filter(java.util.Objects::nonNull).toList());
        return rows.stream().map(g -> new GroupView(g.getId(), g.getBankId(), g.getSortOrder(), g.getTitle(), g.getInstruction(),
                g.getPassageHtml() == null ? null : html.get(g.getPassageHtml()), g.getAudioAssetId(),
                g.getAudioAssetId() == null ? null : durations.getOrDefault(g.getAudioAssetId(), java.util.Optional.empty()).orElse(null),
                g.getTranscriptHtml() == null ? null : html.get(g.getTranscriptHtml()), g.getVideoAssetId(), g.getVideoYoutubeId(),
                g.getVideoDurationSec(), layouts.get(g.getId()))).toList();
    }

    private QuestionGroup find(long id) {
        return groups.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "group_not_found", "Không tìm thấy nhóm câu hỏi"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}

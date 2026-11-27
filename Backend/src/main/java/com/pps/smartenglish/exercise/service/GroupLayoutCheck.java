package com.pps.smartenglish.exercise.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.LayoutKind;
import com.pps.smartenglish.exercise.domain.Question;
import com.pps.smartenglish.exercise.domain.QuestionGroup;
import com.pps.smartenglish.exercise.domain.QuestionType;
import com.pps.smartenglish.exercise.repository.QuestionGroupRepository;
import com.pps.smartenglish.exercise.repository.QuestionRepository;
import com.pps.smartenglish.exercise.validation.LayoutIndex;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.media.service.VideoMediaService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * D-154, D-157: kiểm chéo câu và layout của nhóm. Câu dạng layout phải thuộc nhóm có layout cùng kind; slot phải có trong layout và
 * mỗi slot đúng một câu; option/word của đáp án phải là id trong layout; dạng nghe (DICTATION, LISTEN_FORM) cần nhóm có audio.
 * Chạy khi lưu câu và khi lưu nhóm (đổi layout hoặc audio không được làm hỏng câu đã có).
 */
@Component
public class GroupLayoutCheck {

    private final QuestionGroupRepository groups;
    private final QuestionRepository questions;
    private final VideoMediaService videos;

    GroupLayoutCheck(QuestionGroupRepository groups, QuestionRepository questions, VideoMediaService videos) {
        this.groups = groups;
        this.questions = questions;
        this.videos = videos;
    }

    /**
     * Lưu một câu; selfId null khi tạo mới. Dạng không cần nhóm thì bỏ qua. sortOrder null (tạo mới) coi như câu cuối nhóm;
     * chỉ dùng cho mốc 'at' của Video phản xạ.
     */
    public void checkQuestion(QuestionType type, JsonNode payload, JsonNode key, Long groupId, Long selfId, Integer sortOrder) {
        if (type.layoutKind() == null && !type.needsAudio() && !type.needsGroupVideo()) {
            return;
        }
        QuestionGroup g = groupId == null ? null : groups.findById(groupId).orElse(null);
        if (g == null) {
            throw ApiException.validation("groupId", "Dạng " + type + " phải thuộc một nhóm câu hỏi");
        }
        Set<String> taken = new HashSet<>();
        for (Question q : questions.findByGroupIdAndArchivedAtIsNull(groupId)) {
            if (!q.getId().equals(selfId) && q.getType().layoutKind() != null && q.getPayload() != null) {
                taken.add(q.getPayload().path("slot").asText());
            }
        }
        one(type, payload, key, g, taken);
        if (type.isVideoReflex()) {
            List<Mark> marks = new ArrayList<>(reflexMarks(g, selfId));
            marks.add(new Mark(payload.path("at").asInt(), sortOrder == null ? Integer.MAX_VALUE : sortOrder, true));
            requireIncreasing(g, marks);
        }
    }

    /** Lưu nhóm đã có id: mọi câu còn hiệu lực trong nhóm vẫn phải khớp layout và audio mới. */
    public void checkGroup(QuestionGroup g) {
        Set<String> taken = new HashSet<>();
        for (Question q : questions.findByGroupIdAndArchivedAtIsNull(g.getId())) {
            one(q.getType(), q.getPayload(), q.getAnswerKey(), g, taken);
        }
        requireIncreasing(g, reflexMarks(g, null)); // đổi video/thời lượng không được làm mốc cũ vượt hết video
    }

    private record Mark(int at, int order, boolean self) {}

    /** Mốc của các câu Video phản xạ còn hiệu lực trong nhóm (trừ câu selfId). */
    private List<Mark> reflexMarks(QuestionGroup g, Long selfId) {
        List<Mark> out = new ArrayList<>();
        for (Question q : questions.findByGroupIdAndArchivedAtIsNull(g.getId())) {
            if (q.getType().isVideoReflex() && !q.getId().equals(selfId) && q.getPayload() != null) {
                out.add(new Mark(q.getPayload().path("at").asInt(), q.getSortOrder(), false));
            }
        }
        return out;
    }

    /** at tăng dần theo thứ tự câu (D-158) và nhỏ hơn thời lượng video nếu biết (file: lúc tải lên; YouTube: số CMS nhập). */
    private void requireIncreasing(QuestionGroup g, List<Mark> marks) {
        Integer duration = g.getVideoAssetId() != null ? videos.durationSec(g.getVideoAssetId()).orElse(null) : g.getVideoDurationSec();
        marks.sort(Comparator.comparingInt(Mark::order).thenComparing(Mark::self)); // cùng thứ tự: câu đang lưu xếp sau
        int prev = -1;
        for (Mark m : marks) {
            String field = m.self() ? "payload.at" : "groupId";
            if (m.at() <= prev) {
                throw ApiException.validation(field, "Mốc dừng phải tăng dần theo thứ tự câu trong nhóm");
            }
            if (duration != null && m.at() >= duration) {
                throw ApiException.validation(field, "Mốc dừng phải nhỏ hơn thời lượng video (" + duration + " giây)");
            }
            prev = m.at();
        }
    }

    private static void one(QuestionType type, JsonNode payload, JsonNode key, QuestionGroup g, Set<String> taken) {
        if (type.needsAudio() && g.getAudioAssetId() == null) {
            throw ApiException.validation("groupId", "Dạng " + type + " cần nhóm có audio");
        }
        if (type.needsGroupVideo() && g.getVideoAssetId() == null && g.getVideoYoutubeId() == null) {
            throw ApiException.validation("groupId", "Dạng " + type + " cần nhóm có video");
        }
        if (type.needsGroupVideo() && g.getVideoYoutubeId() != null && g.getVideoDurationSec() == null) {
            throw ApiException.validation("videoDurationSec", "Video YouTube của nhóm có câu " + type + " cần nhập thời lượng (giây)");
        }
        LayoutKind kind = type.layoutKind();
        if (kind == null) {
            return;
        }
        if (g.getLayout() == null) {
            throw ApiException.validation("groupId", "Dạng " + type + " cần nhóm có layout " + kind);
        }
        LayoutIndex idx = LayoutIndex.of(g.getLayout());
        if (idx.kind() != kind) {
            throw ApiException.validation("groupId", "Dạng " + type + " cần layout " + kind + ", nhóm đang là " + idx.kind());
        }
        if (kind.slotBased()) {
            String slot = payload.path("slot").asText();
            if (!idx.slots().contains(slot)) {
                throw ApiException.validation("payload.slot", "Chỗ trống \"" + slot + "\" không có trong layout của nhóm");
            }
            if (!taken.add(slot)) {
                throw ApiException.validation("payload.slot", "Chỗ trống \"" + slot + "\" đã có câu khác");
            }
        }
        String field = switch (kind) {
            case OPTIONS, MAP -> "option";
            case TEXT_BANK -> "word";
            default -> null;
        };
        if (field != null && !idx.ids().contains(key.path(field).asText().toLowerCase(Locale.ROOT))) {
            throw ApiException.validation("answerKey." + field, "Phải là id có trong layout của nhóm");
        }
    }
}

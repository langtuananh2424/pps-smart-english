package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.domain.QuestionMedia;
import com.pps.smartenglish.exercise.dto.QuestionMediaDtos.MediaInput;
import com.pps.smartenglish.exercise.dto.QuestionMediaDtos.QuestionMediaView;
import com.pps.smartenglish.exercise.repository.QuestionMediaRepository;
import com.pps.smartenglish.exercise.repository.QuestionRepository;
import com.pps.smartenglish.media.config.MediaProperties;
import com.pps.smartenglish.media.service.MediaService;
import com.pps.smartenglish.shared.audit.AuditRecorder;
import com.pps.smartenglish.shared.error.ApiException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** D-134: ảnh của câu hỏi. Người soạn thay cả danh sách; người học và CMS đọc qua forQuestions (một truy vấn cho cả bộ). */
@Service
public class QuestionMediaService {

    private final QuestionMediaRepository links;
    private final QuestionRepository questions;
    private final MediaService media;
    private final MediaProperties props;
    private final AuditRecorder audit;

    QuestionMediaService(QuestionMediaRepository links, QuestionRepository questions, MediaService media, MediaProperties props,
                         AuditRecorder audit) {
        this.links = links;
        this.questions = questions;
        this.media = media;
        this.props = props;
        this.audit = audit;
    }

    @Transactional
    public List<QuestionMediaView> replace(long questionId, List<MediaInput> items) {
        if (!questions.existsById(questionId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "question_not_found", "Không tìm thấy câu hỏi");
        }
        if (items.size() > props.maxPerQuestion()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "too_many_images", "Mỗi câu tối đa " + props.maxPerQuestion() + " ảnh");
        }
        var ids = items.stream().map(MediaInput::assetId).toList();
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "duplicate_image", "Một ảnh chỉ gắn một lần vào mỗi câu");
        }
        media.requireImages(ids);
        var before = forQuestions(List.of(questionId)).getOrDefault(questionId, List.of());
        links.deleteByQuestionId(questionId);
        links.flush();
        links.saveAll(IntStream.range(0, items.size()).mapToObj(i -> {
            var m = items.get(i);
            return new QuestionMedia(questionId, m.assetId(), m.role(), m.displaySize(), m.altText(), i);
        }).toList());
        links.flush();
        var after = forQuestions(List.of(questionId)).getOrDefault(questionId, List.of());
        audit.record("UPDATE", "QUESTION_MEDIA", questionId, ids(before), ids(after));
        return after;
    }

    @Transactional(readOnly = true)
    public Map<Long, List<QuestionMediaView>> forQuestions(Collection<Long> questionIds) {
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        var rows = links.findByQuestionIdInOrderByQuestionIdAscSortOrderAscIdAsc(questionIds);
        var files = media.files(rows.stream().map(QuestionMedia::getAssetId).toList());
        return rows.stream().filter(r -> files.containsKey(r.getAssetId())).collect(Collectors.groupingBy(
                QuestionMedia::getQuestionId, java.util.LinkedHashMap::new, Collectors.mapping(r -> {
                    var f = files.get(r.getAssetId());
                    return new QuestionMediaView(f.id(), f.url(), f.width(), f.height(), r.getRole(), r.getDisplaySize(), r.getAltText());
                }, Collectors.toList())));
    }

    private static List<Long> ids(List<QuestionMediaView> views) {
        return views.stream().map(QuestionMediaView::assetId).toList();
    }
}

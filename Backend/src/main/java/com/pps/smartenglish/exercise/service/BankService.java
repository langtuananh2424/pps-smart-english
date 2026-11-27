package com.pps.smartenglish.exercise.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pps.smartenglish.exercise.content.LayoutRenderer;
import com.pps.smartenglish.exercise.content.RichText;
import com.pps.smartenglish.exercise.domain.Question;
import com.pps.smartenglish.exercise.domain.QuestionBank;
import com.pps.smartenglish.exercise.domain.QuestionChoice;
import com.pps.smartenglish.exercise.domain.QuestionGroup;
import com.pps.smartenglish.exercise.domain.QuestionHint;
import com.pps.smartenglish.exercise.domain.QuestionType;
import com.pps.smartenglish.exercise.dto.BankResponse;
import com.pps.smartenglish.exercise.dto.BankResponse.ChoiceView;
import com.pps.smartenglish.exercise.dto.BankResponse.GroupView;
import com.pps.smartenglish.exercise.dto.BankResponse.HintView;
import com.pps.smartenglish.exercise.dto.BankResponse.QuestionView;
import com.pps.smartenglish.exercise.dto.QuestionMediaDtos.QuestionMediaView;
import com.pps.smartenglish.exercise.listening.AudioMediaService;
import com.pps.smartenglish.exercise.listening.AudioPlayService;
import com.pps.smartenglish.media.service.VideoMediaService;
import com.pps.smartenglish.exercise.repository.QuestionBankRepository;
import com.pps.smartenglish.exercise.repository.QuestionGroupRepository;
import com.pps.smartenglish.exercise.repository.QuestionHintRepository;
import com.pps.smartenglish.exercise.repository.StudentAttemptRepository;
import com.pps.smartenglish.exercise.domain.StudentAttempt;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-EX-01, FR-EX-02: đề bài cho học viên; chỉ chuyển các trường an toàn sang DTO. */
@Service
public class BankService {

    private final BankGate gate;
    private final QuestionLoader loader;
    private final ExamService exam;
    private final QuestionHintRepository hints;
    private final QuestionMediaService media;
    private final QuestionGroupRepository groups;
    private final RichText rich;
    private final QuestionBankRepository banks;
    private final AudioPlayService audioPlays;
    private final AudioMediaService audio;
    private final VideoMediaService video;
    private final StudentAttemptRepository attempts;
    private final AttemptModes modes;
    private final LayoutRenderer layouts;

    BankService(BankGate gate, QuestionLoader loader, ExamService exam, QuestionHintRepository hints, QuestionMediaService media,
                QuestionGroupRepository groups, RichText rich, QuestionBankRepository banks, AudioPlayService audioPlays,
                AudioMediaService audio, VideoMediaService video, StudentAttemptRepository attempts, AttemptModes modes,
                LayoutRenderer layouts) {
        this.layouts = layouts;
        this.video = video;
        this.modes = modes;
        this.attempts = attempts;
        this.banks = banks;
        this.audioPlays = audioPlays;
        this.audio = audio;
        this.groups = groups;
        this.rich = rich;
        this.gate = gate;
        this.loader = loader;
        this.exam = exam;
        this.hints = hints;
        this.media = media;
    }

    @Transactional(readOnly = true)
    public BankResponse get(Long bankId, Long userId) {
        exam.guardView(userId, bankId); // trước gate: cùng mã lỗi exam_* như lúc bắt đầu thi
        var bank = gate.requireReadable(bankId, userId);
        var set = loader.load(bankId);
        var playback = banks.findById(bankId).orElseThrow(); // findInfo vừa tìm thấy bộ này
        // D-155: lượt Thi (đang mở, hoặc bài giao Thi) không có gợi ý; lượt Luyện tập lấy qua /attempts/{id}/hints
        var examNow = attempts.findFirstByUserIdAndBankIdAndSubmittedAtIsNullOrderByIdDesc(userId, bankId)
                .map(a -> !modes.practiceAllowed(a)).orElseGet(() -> modes.examAssigned(userId, bankId));
        var hintsByQuestion = examNow ? Map.<Long, List<HintView>>of() : hintsOf(bank.mode(), set.questions());
        var groupRows = groups.findByBankIdOrderBySortOrderAscIdAsc(bankId);
        // một lần gọi kho ảnh cho mọi HTML của đề (đề bài, lựa chọn, bài đọc)
        var html = rich.render(Stream.of(set.questions().stream().map(Question::getPromptHtml),
                set.choices().values().stream().flatMap(List::stream).map(QuestionChoice::getContentHtml),
                groupRows.stream().map(QuestionGroup::getPassageHtml)).flatMap(s -> s).toList());
        var durations = audio.durations(groupRows.stream().map(QuestionGroup::getAudioAssetId).filter(java.util.Objects::nonNull).toList());
        var videoUrls = video.signedUrls(groupRows.stream().map(QuestionGroup::getVideoAssetId).filter(java.util.Objects::nonNull).toList());
        var layoutByGroup = layouts.render(groupRows);
        long attemptId = attempts.findFirstByUserIdAndBankIdAndSubmittedAtIsNullOrderByIdDesc(userId, bankId)
                .map(StudentAttempt::getId).orElse(0L); // chưa có lượt mở: seed 0, vẫn khác thứ tự soạn
        return new BankResponse(bank.id(), bank.title(), bank.lessonId(), bank.trial(), bank.mode(),
                bank.timeLimitSec(), questionViews(set, hintsByQuestion,
                        media.forQuestions(set.questions().stream().map(Question::getId).toList()), html, attemptId),
                groupRows.stream().map(g -> new GroupView(g.getId(), g.getTitle(), g.getInstruction(),
                        rendered(html, g.getPassageHtml()), g.getSortOrder(), g.getAudioAssetId() != null && durations.containsKey(g.getAudioAssetId()),
                        g.getAudioAssetId() == null ? null : durations.getOrDefault(g.getAudioAssetId(), java.util.Optional.empty()).orElse(null),
                        g.getVideoAssetId() == null ? null : videoUrls.get(g.getVideoAssetId()), g.getVideoYoutubeId(),
                        layoutByGroup.get(g.getId()))).toList(),
                playback.getPlaybackMode(), audioPlays.limitOf(playback), banks.findSkill(bankId).orElse(null),
                banks.findIelts(bankId).orElse(false));
    }

    /** Kiểm tra đầu vào dùng bản này: chưa có HTML giàu định dạng. */
    public static List<QuestionView> questionViews(QuestionSet set, Map<Long, List<HintView>> hintsByQuestion,
                                                   Map<Long, List<QuestionMediaView>> mediaByQuestion, long attemptId) {
        return questionViews(set, hintsByQuestion, mediaByQuestion, Map.of(), attemptId);
    }

    /** Đề cho người làm bài: không đáp án. Dùng chung với kiểm tra đầu vào (module placement). */
    public static List<QuestionView> questionViews(QuestionSet set, Map<Long, List<HintView>> hintsByQuestion,
                                                   Map<Long, List<QuestionMediaView>> mediaByQuestion,
                                                   Map<String, String> html, long attemptId) {
        return set.questions().stream().map(q -> new QuestionView(q.getId(), q.getType().name(), q.getInstruction(), q.getPrompt(),
                payloadFor(q, attemptId),
                q.getType().usesChoices()
                        ? set.choicesOf(q).stream().map(c -> new ChoiceView(c.getId(), c.getLabel(), c.getContent(),
                                rendered(html, c.getContentHtml()))).toList()
                        : null,
                hintsByQuestion.getOrDefault(q.getId(), List.of()),
                mediaByQuestion.getOrDefault(q.getId(), List.of()), q.getGroupId(), rendered(html, q.getPromptHtml()))).toList();
    }

    private static String rendered(Map<String, String> html, String stored) {
        return stored == null ? null : html.get(stored);
    }

    /** FR-AI-06: chỉ bộ PRACTICE có gợi ý; một truy vấn cho cả bank. */
    private Map<Long, List<HintView>> hintsOf(String mode, List<Question> qs) {
        if (!QuestionBank.PRACTICE.equals(mode) || qs.isEmpty()) {
            return Map.of();
        }
        return hints.findByQuestionIdInOrderBySortOrderAscIdAsc(qs.stream().map(Question::getId).toList()).stream()
                .collect(Collectors.groupingBy(QuestionHint::getQuestionId,
                        Collectors.mapping(h -> new HintView(h.getId(), h.getContent(), h.getDetail()), Collectors.toList())));
    }

    /** D-44: GRAMMAR_IDENTIFY báo số cụm cần tìm (tính lúc trả, dữ liệu cũ cũng có); chỉ đưa ra số đếm, không lộ spans. */
    private static JsonNode payloadFor(Question q, long attemptId) {
        if (q.getType() == QuestionType.WORD_BANK) {
            return shuffled(q, attemptId, "words");
        }
        if (q.getType() == QuestionType.LETTER_ORDER) {
            return shuffled(q, attemptId, "letters"); // id lưu đã ngẫu nhiên; xáo thêm để không lộ thứ tự lưu
        }
        if (q.getType() != QuestionType.GRAMMAR_IDENTIFY) {
            return q.getPayload();
        }
        ObjectNode out = q.getPayload() != null && q.getPayload().isObject()
                ? ((ObjectNode) q.getPayload()).deepCopy() : JsonNodeFactory.instance.objectNode();
        return out.put("spanCount", q.getAnswerKey() == null ? 0 : q.getAnswerKey().path("spans").size());
    }

    /** D-146: xáo hộp từ tất định theo (lượt, câu) để không đoán đáp án theo vị trí; đáp án chấm theo giá trị từ nên không ảnh hưởng. */
    private static JsonNode shuffled(Question q, long attemptId, String field) {
        if (q.getPayload() == null || !q.getPayload().path(field).isArray()) {
            return q.getPayload();
        }
        var words = new ArrayList<JsonNode>();
        q.getPayload().get(field).forEach(words::add);
        Collections.shuffle(words, new Random(attemptId * 1_000_003L + q.getId()));
        ObjectNode out = ((ObjectNode) q.getPayload()).deepCopy();
        out.putArray(field).addAll(words);
        return out;
    }
}

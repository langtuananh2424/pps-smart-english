package com.pps.smartenglish.exercise.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pps.smartenglish.exercise.domain.Question;
import com.pps.smartenglish.exercise.domain.QuestionBank;
import com.pps.smartenglish.exercise.domain.QuestionHint;
import com.pps.smartenglish.exercise.domain.QuestionType;
import com.pps.smartenglish.exercise.dto.BankResponse;
import com.pps.smartenglish.exercise.dto.BankResponse.ChoiceView;
import com.pps.smartenglish.exercise.dto.BankResponse.HintView;
import com.pps.smartenglish.exercise.dto.BankResponse.QuestionView;
import com.pps.smartenglish.exercise.dto.QuestionMediaDtos.QuestionMediaView;
import com.pps.smartenglish.exercise.repository.QuestionHintRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

    BankService(BankGate gate, QuestionLoader loader, ExamService exam, QuestionHintRepository hints, QuestionMediaService media) {
        this.gate = gate;
        this.loader = loader;
        this.exam = exam;
        this.hints = hints;
        this.media = media;
    }

    @Transactional(readOnly = true)
    public BankResponse get(Long bankId, Long userId) {
        exam.guardView(userId, bankId); // trước gate: cùng mã lỗi exam_* như lúc bắt đầu thi
        var bank = gate.require(bankId, userId);
        var set = loader.load(bankId);
        var hintsByQuestion = hintsOf(bank.mode(), set.questions());
        return new BankResponse(bank.id(), bank.title(), bank.lessonId(), bank.trial(), bank.mode(),
                bank.timeLimitSec(), questionViews(set, hintsByQuestion,
                        media.forQuestions(set.questions().stream().map(Question::getId).toList())));
    }

    /** Đề cho người làm bài: không đáp án. Dùng chung với kiểm tra đầu vào (module placement). */
    public static List<QuestionView> questionViews(QuestionSet set, Map<Long, List<HintView>> hintsByQuestion,
                                                   Map<Long, List<QuestionMediaView>> mediaByQuestion) {
        return set.questions().stream().map(q -> new QuestionView(q.getId(), q.getType().name(), q.getInstruction(), q.getPrompt(),
                payloadFor(q),
                q.getType() == QuestionType.CONNECTIVE_MCQ
                        ? set.choicesOf(q).stream().map(c -> new ChoiceView(c.getId(), c.getLabel(), c.getContent())).toList()
                        : null,
                hintsByQuestion.getOrDefault(q.getId(), List.of()),
                mediaByQuestion.getOrDefault(q.getId(), List.of()))).toList();
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
    private static JsonNode payloadFor(Question q) {
        if (q.getType() != QuestionType.GRAMMAR_IDENTIFY) {
            return q.getPayload();
        }
        ObjectNode out = q.getPayload() != null && q.getPayload().isObject()
                ? ((ObjectNode) q.getPayload()).deepCopy() : JsonNodeFactory.instance.objectNode();
        return out.put("spanCount", q.getAnswerKey() == null ? 0 : q.getAnswerKey().path("spans").size());
    }
}

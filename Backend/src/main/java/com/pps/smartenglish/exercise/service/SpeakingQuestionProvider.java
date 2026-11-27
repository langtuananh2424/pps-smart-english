package com.pps.smartenglish.exercise.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.ai.config.VideoReflexProperties;
import com.pps.smartenglish.ai.speaking.SpeakingQuestionSource;
import com.pps.smartenglish.exercise.domain.QuestionType;
import com.pps.smartenglish.exercise.repository.QuestionBankRepository;
import com.pps.smartenglish.exercise.repository.QuestionRepository;
import com.pps.smartenglish.shared.error.ApiException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Cấp câu nói cho module ai, kiểm quyền vào bộ câu hỏi bằng BankGate như mọi bài tập khác (bank EXAM cũng qua các policy). */
@Component
class SpeakingQuestionProvider implements SpeakingQuestionSource {

    /** D-43: part 2 tối đa 3 phút, part 1 và 3 tối đa 1 phút. */
    static final int PART2_SECONDS = 180;
    static final int SHORT_PART_SECONDS = 60;

    private final QuestionRepository questions;
    private final QuestionBankRepository banks;
    private final BankGate gate;
    private final VideoReflexProperties reflex;

    SpeakingQuestionProvider(QuestionRepository questions, QuestionBankRepository banks, BankGate gate, VideoReflexProperties reflex) {
        this.reflex = reflex;
        this.questions = questions;
        this.banks = banks;
        this.gate = gate;
    }

    @Override
    @Transactional(readOnly = true)
    public SpeakingQuestion find(long questionId, long userId, boolean inAssignment, boolean inAttempt) {
        var q = questions.findById(questionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "question_not_found", "Không tìm thấy câu hỏi"));
        if (!q.getType().isSpeakingLike()) {
            throw ApiException.validation("questionId", "Không phải câu hỏi nói");
        }
        gate.requireFor(q.getBankId(), userId, inAssignment, inAttempt);
        JsonNode p = q.getPayload();
        boolean video = q.getType().isVideoReflex();
        int part = q.getType() == QuestionType.SPEAKING ? p.path("part").asInt() : 1; // LISTEN_RECORD, Video phản xạ không có part: chấm như câu ngắn part 1
        int fallback = video ? (q.getType() == QuestionType.VIDEO_SHORT ? reflex.shortSeconds() : reflex.otherSeconds())
                : part == 2 ? PART2_SECONDS : SHORT_PART_SECONDS;
        int max = p.path("maxSeconds").isIntegralNumber() ? p.path("maxSeconds").asInt() : fallback;
        List<String> cue = new ArrayList<>();
        p.path("cueCard").forEach(c -> cue.add(c.asText()));
        return new SpeakingQuestion(q.getId(), part, p.path("prompt").asText(), cue, max,
                banks.findCourseTitle(q.getBankId()).orElse(""), banks.findCourseId(q.getBankId()).orElse(null),
                video ? reflex.maxRecordings() : 0, q.getType() == QuestionType.VIDEO_SHORT);
    }

    @Override
    @Transactional(readOnly = true)
    public SpeakingQuestion findForNewSession(long questionId, long userId, boolean inAssignment, boolean inAttempt) {
        var q = find(questionId, userId, inAssignment, inAttempt);
        WritingQuestionProvider.requireNotArchived(questions.findById(questionId).orElseThrow());
        return q;
    }
}

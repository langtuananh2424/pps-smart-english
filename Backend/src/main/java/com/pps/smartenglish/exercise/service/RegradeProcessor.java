package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.ai.service.AiJobService;
import com.pps.smartenglish.exercise.config.RegradeProperties;
import com.pps.smartenglish.exercise.domain.AnswerGrading;
import com.pps.smartenglish.exercise.domain.Question;
import com.pps.smartenglish.exercise.domain.QuestionChoice;
import com.pps.smartenglish.exercise.domain.RegradeItem;
import com.pps.smartenglish.exercise.domain.RegradeRun;
import com.pps.smartenglish.exercise.grading.GraderRegistry;
import com.pps.smartenglish.exercise.repository.QuestionChoiceRepository;
import com.pps.smartenglish.exercise.repository.QuestionRepository;
import com.pps.smartenglish.exercise.repository.RegradeItemRepository;
import com.pps.smartenglish.exercise.repository.RegradeRunRepository;
import com.pps.smartenglish.exercise.repository.StudentAnswerRepository;
import com.pps.smartenglish.exercise.service.RegradeApplier.Outcome;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Xử lý đúng một item PENDING trong một transaction riêng (lỗi một item không kéo cả lô). Chấm theo đáp án HIỆN TẠI của câu:
 * dạng tự chấm bằng GraderRegistry; câu viết lại bằng RewriteRules rồi mới tới AI (đúng một job mỗi item, không trừ lượt).
 * shortcut: không khóa hàng giữa lúc học viên nộp và lúc Admin sửa; bài nộp chen đúng lúc xác nhận có thể sót, lần chấm lại sau
 * (sau khi Admin bấm lại) sẽ bắt được.
 */
@Component
class RegradeProcessor {

    private final RegradeItemRepository items;
    private final RegradeRunRepository runs;
    private final QuestionRepository questions;
    private final QuestionChoiceRepository choices;
    private final StudentAnswerRepository answers;
    private final GraderRegistry graders;
    private final RewriteRules rules;
    private final RewriteJobInput rewriteInput;
    private final AiJobService aiJobs;
    private final RegradeApplier applier;
    private final RegradeProperties props;

    RegradeProcessor(RegradeItemRepository items, RegradeRunRepository runs, QuestionRepository questions,
                     QuestionChoiceRepository choices, StudentAnswerRepository answers, GraderRegistry graders, RewriteRules rules,
                     RewriteJobInput rewriteInput, AiJobService aiJobs, RegradeApplier applier, RegradeProperties props) {
        this.items = items;
        this.runs = runs;
        this.questions = questions;
        this.choices = choices;
        this.answers = answers;
        this.graders = graders;
        this.rules = rules;
        this.rewriteInput = rewriteInput;
        this.aiJobs = aiJobs;
        this.applier = applier;
        this.props = props;
    }

    @Transactional
    public void process(long itemId) {
        if (items.lockPending(itemId).isEmpty()) {
            return; // instance khác đang giữ hoặc đã xử lý
        }
        RegradeItem item = items.findById(itemId).orElseThrow();
        RegradeRun run = runs.findById(item.getRunId()).orElseThrow();
        Question q = questions.findById(run.getQuestionId()).orElseThrow();
        if (!RegradeRun.RUNNING.equals(run.getStatus()) || run.getQuestionVersion() != q.getAnswerVersion()) {
            return; // run đã cũ: tick sau đánh dấu SUPERSEDED và hủy item
        }
        var answer = answers.findById(item.getAnswerId()).orElseThrow();
        if (!aiJobs.pendingIds(answer.getAttemptId()).isEmpty()) {
            return; // job chấm lần đầu của lượt này còn chạy: hoãn để hai bên không cùng ghi
        }
        if (!ResultAssembler.REWRITE_TYPES.contains(q.getType())) {
            Boolean correct = graders.grade(q.getType(), inputOf(q), answer.getAnswer());
            applier.apply(itemId, RegradeItem.PENDING, new Outcome(Boolean.TRUE.equals(correct), null, null, null));
            return;
        }
        var r = rules.check(q, answer.getAnswer());
        switch (r.verdict()) {
            case WRONG -> applier.apply(itemId, RegradeItem.PENDING, new Outcome(false, AnswerGrading.RULE, r.feedback(), r.corrected()));
            case CORRECT -> applier.apply(itemId, RegradeItem.PENDING, new Outcome(true, AnswerGrading.RULE, r.feedback(), r.corrected()));
            case NEEDS_AI -> enqueue(item, run, q, answer.getAttemptId(), answer.getAnswer());
        }
    }

    /** Chỉ tạo job khi số job chấm lại đang chạy dưới trần; còn lại để tick sau. */
    private void enqueue(RegradeItem item, RegradeRun run, Question q, long attemptId, com.fasterxml.jackson.databind.JsonNode given) {
        if (aiJobs.regradeInFlight() >= props.maxAiPending()) {
            return;
        }
        var input = rewriteInput.build(attemptId, Map.of(q.getId(), q), Map.of(q.getId(), given));
        long jobId = aiJobs.enqueueRegrade(run.getRequestedBy(), input, attemptId, item.getId());
        items.markWaiting(item.getId(), jobId);
    }

    private com.pps.smartenglish.exercise.grading.GradingInput inputOf(Question q) {
        List<QuestionChoice> own = choices.findByQuestionIdInOrderByLabelAscIdAsc(List.of(q.getId()));
        return new QuestionSet(List.of(q), Map.of(q.getId(), own)).inputOf(q);
    }
}

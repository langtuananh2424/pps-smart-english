package com.pps.smartenglish.exercise.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.pps.smartenglish.ai.dto.RewriteGrades;
import com.pps.smartenglish.exercise.domain.AnswerGrading;
import com.pps.smartenglish.exercise.domain.RegradeItem;
import com.pps.smartenglish.exercise.domain.RegradeRun;
import com.pps.smartenglish.exercise.repository.AnswerGradingRepository;
import com.pps.smartenglish.exercise.repository.QuestionRepository;
import com.pps.smartenglish.exercise.repository.RegradeItemRepository;
import com.pps.smartenglish.exercise.repository.RegradeRunRepository;
import com.pps.smartenglish.exercise.repository.StudentAnswerRepository;
import com.pps.smartenglish.exercise.repository.StudentAttemptRepository;
import com.pps.smartenglish.shared.event.ExerciseScored;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Áp một kết quả chấm lại trong MỘT transaction: giành quyền xử lý item bằng UPDATE có điều kiện status (không bao giờ áp hai
 * lần), ghi điểm cũ/mới, sửa is_correct, cộng điểm lượt làm bài nguyên tử, thay student_answer_grading. Không trừ XP, không
 * đổi streak, không thông báo; điểm tăng thì phát ExerciseScored initial=false để retention xét thưởng điểm cao.
 */
@Component
class RegradeApplier {

    /** grader null = dạng tự chấm (không có dòng student_answer_grading). */
    record Outcome(boolean correct, String grader, String feedback, String correctedAnswer) {}

    private final RegradeItemRepository items;
    private final RegradeRunRepository runs;
    private final QuestionRepository questions;
    private final StudentAnswerRepository answers;
    private final StudentAttemptRepository attempts;
    private final AnswerGradingRepository gradings;
    private final ObjectMapper mapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    RegradeApplier(RegradeItemRepository items, RegradeRunRepository runs, QuestionRepository questions,
                   StudentAnswerRepository answers, StudentAttemptRepository attempts, AnswerGradingRepository gradings,
                   ObjectMapper mapper, ApplicationEventPublisher events, Clock clock) {
        this.items = items;
        this.runs = runs;
        this.questions = questions;
        this.answers = answers;
        this.attempts = attempts;
        this.gradings = gradings;
        this.mapper = mapper;
        this.events = events;
        this.clock = clock;
    }

    /** Kết quả AI cho item đang AI_WAITING. Run đã bị thay thế hoặc item đã hủy/xong thì bỏ qua, không đổi điểm. */
    @Transactional
    public void applyAi(long itemId, RewriteGrades.Item graded) {
        RegradeItem item = items.findById(itemId).orElse(null);
        if (item == null || !RegradeItem.AI_WAITING.equals(item.getStatus())) {
            return;
        }
        RegradeRun run = runs.findById(item.getRunId()).orElseThrow();
        var question = questions.findById(run.getQuestionId()).orElseThrow();
        if (!RegradeRun.RUNNING.equals(run.getStatus()) || run.getQuestionVersion() != question.getAnswerVersion()) {
            items.transition(itemId, RegradeItem.AI_WAITING, RegradeItem.CANCELLED); // job đến muộn sau khi Admin sửa tiếp
            return;
        }
        if (graded.id() == question.getId()) {
            apply(itemId, RegradeItem.AI_WAITING, new Outcome(graded.correct(), AnswerGrading.AI, graded.feedback(),
                    graded.correctedAnswer()));
        }
    }

    /** false = item không còn ở trạng thái from (đã xử lý hoặc bị hủy): không đổi gì. */
    @Transactional
    public boolean apply(long itemId, String from, Outcome outcome) {
        if (items.transition(itemId, from, RegradeItem.DONE) == 0) {
            return false;
        }
        RegradeItem item = items.findById(itemId).orElseThrow();
        var answer = answers.findById(item.getAnswerId()).orElseThrow();
        var attempt = attempts.findById(answer.getAttemptId()).orElseThrow();
        Boolean oldCorrect = answer.getCorrect();
        JsonNode oldResult = oldResult(answer.getId());

        int delta = bit(outcome.correct()) - bit(oldCorrect);
        answers.setVerdict(answer.getId(), outcome.correct());
        if (delta != 0) {
            attempts.adjustScore(attempt.getId(), delta); // score = score + delta nguyên tử, null tính 0
        }
        int newScore = attempts.scoreOf(attempt.getId());
        JsonNode newResult = null;
        if (outcome.grader() != null) {
            gradings.deleteByAnswerId(answer.getId());
            var result = mapper.createObjectNode().put("feedback", outcome.feedback()).put("correctedAnswer", outcome.correctedAnswer());
            gradings.save(new AnswerGrading(answer.getId(), outcome.grader(), bit(outcome.correct()), result));
            var wrapped = mapper.createObjectNode().put("grader", outcome.grader()).put("score", bit(outcome.correct()));
            wrapped.set("result", result);
            newResult = wrapped;
        }
        // mọi UPDATE hàng loạt ở trên đã xong: nạp lại item rồi ghi điểm cũ/mới (entity cũ đã bị tách)
        items.findById(itemId).orElseThrow().record(oldCorrect, outcome.correct(), newScore - delta, newScore, oldResult,
                newResult, Instant.now(clock));
        if (delta > 0) {
            int max = attempt.getMaxScore() == null ? 0 : attempt.getMaxScore();
            events.publishEvent(new ExerciseScored(attempt.getUserId(), attempt.getBankId(), attempt.getId(), newScore, max, false));
        }
        return true;
    }

    /** Kết quả chấm cũ của câu trả lời, giữ nguyên để Admin đối chiếu: [{grader, score, result}] hoặc null nếu chưa có. */
    private JsonNode oldResult(long answerId) {
        var rows = gradings.findByAnswerIdIn(java.util.List.of(answerId));
        if (rows.isEmpty()) {
            return null;
        }
        ArrayNode out = mapper.createArrayNode();
        for (AnswerGrading g : rows) {
            var one = mapper.createObjectNode().put("grader", g.getGrader()).put("score", g.getScore());
            one.set("result", g.getResult());
            out.add(one);
        }
        return out;
    }

    private static int bit(Boolean b) {
        return Boolean.TRUE.equals(b) ? 1 : 0;
    }
}

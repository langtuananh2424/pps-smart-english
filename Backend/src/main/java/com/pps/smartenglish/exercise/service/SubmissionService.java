package com.pps.smartenglish.exercise.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.Question;
import com.pps.smartenglish.exercise.domain.StudentAnswer;
import com.pps.smartenglish.exercise.dto.AttemptResponse;
import com.pps.smartenglish.exercise.dto.SubmitRequest;
import com.pps.smartenglish.exercise.dto.SubmitRequest.AnswerInput;
import com.pps.smartenglish.exercise.grading.GraderRegistry;
import com.pps.smartenglish.exercise.repository.StudentAnswerRepository;
import com.pps.smartenglish.exercise.repository.StudentAttemptRepository;
import com.pps.smartenglish.shared.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-EX-03: chấm tại chỗ và nộp đúng một lần. */
@Service
public class SubmissionService {

    private final StudentAttemptRepository attempts;
    private final StudentAnswerRepository answers;
    private final QuestionLoader loader;
    private final GraderRegistry graders;
    private final ResultAssembler assembler;
    private final Clock clock;

    SubmissionService(StudentAttemptRepository attempts, StudentAnswerRepository answers, QuestionLoader loader,
                      GraderRegistry graders, ResultAssembler assembler, Clock clock) {
        this.attempts = attempts;
        this.answers = answers;
        this.loader = loader;
        this.graders = graders;
        this.assembler = assembler;
        this.clock = clock;
    }

    @Transactional
    public AttemptResponse submit(long userId, Long attemptId, SubmitRequest req) {
        var attempt = attempts.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "attempt_not_found", "Không tìm thấy lượt làm bài"));
        if (attempt.getSubmittedAt() != null) {
            throw alreadySubmitted();
        }
        var set = loader.load(attempt.getBankId());
        Map<Long, JsonNode> given = indexAnswers(req.answers(), set.questions());

        int score = 0;
        int maxScore = 0;
        List<StudentAnswer> rows = new ArrayList<>();
        for (Question q : set.questions()) {
            JsonNode answer = given.get(q.getId());
            Boolean correct = graders.grade(q.getType(), set.inputOf(q), answer);
            if (correct != null) {
                maxScore++;
                score += correct ? 1 : 0;
            }
            rows.add(new StudentAnswer(attemptId, q.getId(), answer, correct));
        }

        // Cổng chặn nộp đồng thời: UPDATE theo submitted_at IS NULL; thua cuộc thì rollback, không ghi student_answers
        Instant now = Instant.now(clock);
        if (attempts.claimSubmission(attemptId, now, score, maxScore) == 0) {
            throw alreadySubmitted();
        }
        answers.saveAll(rows);
        return assembler.submitted(attempt, now, score, maxScore, set, rows);
    }

    /** questionId phải thuộc bank và không lặp; answer null/JSON null = bỏ trống (lưu SQL NULL). */
    private static Map<Long, JsonNode> indexAnswers(List<AnswerInput> inputs, List<Question> questions) {
        Set<Long> valid = questions.stream().map(Question::getId).collect(Collectors.toSet());
        Map<Long, JsonNode> out = new HashMap<>();
        for (int i = 0; i < inputs.size(); i++) {
            Long id = inputs.get(i).questionId();
            String field = "answers[" + i + "].questionId";
            if (!valid.contains(id)) {
                throw ApiException.validation(field, "Câu hỏi không thuộc bộ câu hỏi này");
            }
            JsonNode answer = inputs.get(i).answer();
            if (out.containsKey(id)) {
                throw ApiException.validation(field, "Câu hỏi bị lặp");
            }
            out.put(id, answer == null || answer.isNull() ? null : answer);
        }
        return out;
    }

    private static ApiException alreadySubmitted() {
        return new ApiException(HttpStatus.CONFLICT, "attempt_already_submitted", "Lượt làm bài này đã được nộp");
    }
}

package com.pps.smartenglish.exercise.listening;

import com.pps.smartenglish.exercise.domain.Question;
import com.pps.smartenglish.exercise.domain.QuestionGroup;
import com.pps.smartenglish.exercise.domain.StudentAnswer;
import com.pps.smartenglish.exercise.domain.StudentAttempt;
import com.pps.smartenglish.exercise.listening.ListeningDtos.ForcedResult;
import com.pps.smartenglish.exercise.listening.ListeningDtos.HardQuestion;
import com.pps.smartenglish.exercise.listening.ListeningDtos.PartResult;
import com.pps.smartenglish.exercise.listening.ListeningDtos.RuleView;
import com.pps.smartenglish.exercise.listening.ListeningStatsRepository.WrongCount;
import com.pps.smartenglish.exercise.repository.QuestionGroupRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-EX-10, D-136: kết quả Nghe ép. Chỉ số câu đúng (tổng và từng part) và đã đạt ngưỡng chưa; không đáp án, không đúng/sai từng
 * câu. Đạt hay không chỉ để thông báo, KHÔNG mở khóa gì và không chặn học tuần tự (D-136). Cứ mỗi hintEveryN lượt đã nộp thì kèm
 * danh sách câu sai nhiều để học viên chọn xem script.
 */
@Service
public class ForcedResultService {

    private final ForcedRules rules;
    private final ListeningStatsRepository stats;
    private final QuestionGroupRepository groups;

    ForcedResultService(ForcedRules rules, ListeningStatsRepository stats, QuestionGroupRepository groups) {
        this.rules = rules;
        this.stats = stats;
        this.groups = groups;
    }

    @Transactional(readOnly = true)
    public ForcedResult summary(StudentAttempt a, List<Question> questions, List<StudentAnswer> answers) {
        RuleView rule = rules.forAttempt(a);
        Set<Long> right = new HashSet<>();
        answers.stream().filter(s -> Boolean.TRUE.equals(s.getCorrect())).forEach(s -> right.add(s.getQuestionId()));
        int total = questions.size();
        int correct = (int) questions.stream().filter(q -> right.contains(q.getId())).count();

        Map<Long, QuestionGroup> titles = groups.findAllById(questions.stream().map(Question::getGroupId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(QuestionGroup::getId, Function.identity()));
        Map<Long, List<Question>> byGroup = new LinkedHashMap<>();
        questions.stream().filter(q -> q.getGroupId() != null).forEach(q -> byGroup.computeIfAbsent(q.getGroupId(), k -> new ArrayList<>()).add(q));
        boolean byPart = rule.byPart();
        List<PartResult> parts = new ArrayList<>();
        Map<Long, ForcedPass.Part> partCounts = new java.util.HashMap<>();
        for (var e : byGroup.entrySet()) {
            int partTotal = e.getValue().size();
            int partRight = (int) e.getValue().stream().filter(q -> right.contains(q.getId())).count();
            Integer pct = byPart ? rule.thresholdOf(e.getKey()) : null;
            Boolean partPassed = pct == null ? null : ForcedPass.atLeast(partRight, partTotal, pct);
            partCounts.put(e.getKey(), new ForcedPass.Part(partRight, partTotal));
            parts.add(new PartResult(e.getKey(), titles.get(e.getKey()).getTitle(), partRight, partTotal, pct, partPassed));
        }
        boolean passed = ForcedPass.passed(rule, correct, total, partCounts); // D-142: chọn một, ngưỡng kia bị bỏ qua
        int attemptNo = stats.submittedCount(a.getUserId(), a.getBankId(), a.getAssignmentId(), a.getId());
        Map<Long, String> prompts = questions.stream().collect(Collectors.toMap(Question::getId, Question::getPrompt));
        List<HardQuestion> hard = hardQuestions(a, rule)
                .map(list -> list.stream().map(w -> new HardQuestion(w.questionId(), prompts.get(w.questionId()), w.wrongCount())).toList())
                .orElse(null);
        return new ForcedResult(correct, total, total == 0 ? 0 : Math.round(correct * 100f / total),
                byPart ? null : rule.passPctTotal(), passed, parts,
                attemptNo, rule.hintEveryN(), hard);
    }

    /** Có mặt chỉ khi số lượt đã nộp (tới lượt này) chia hết cho N; các câu sai từ wrongThreshold lần, nhiều lần sai trước. */
    Optional<List<WrongCount>> hardQuestions(StudentAttempt a, RuleView rule) {
        int k = stats.submittedCount(a.getUserId(), a.getBankId(), a.getAssignmentId(), a.getId());
        if (k == 0 || k % rule.hintEveryN() != 0) {
            return Optional.empty();
        }
        return Optional.of(stats.hardQuestions(a.getUserId(), a.getBankId(), a.getAssignmentId(), a.getId(), rule.wrongThreshold()));
    }
}

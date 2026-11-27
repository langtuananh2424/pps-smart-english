package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.domain.ExamBandRule;
import com.pps.smartenglish.exercise.domain.Question;
import com.pps.smartenglish.exercise.domain.QuestionType;
import com.pps.smartenglish.exercise.domain.StudentAnswer;
import com.pps.smartenglish.exercise.domain.StudentAttempt;
import com.pps.smartenglish.exercise.dto.ExamBandDtos.AttemptBands;
import com.pps.smartenglish.exercise.dto.ExamBandDtos.SkillBand;
import com.pps.smartenglish.exercise.grading.GraderRegistry;
import com.pps.smartenglish.exercise.repository.ExamBandRuleRepository;
import com.pps.smartenglish.exercise.repository.QuestionRepository;
import com.pps.smartenglish.exercise.repository.StudentAnswerRepository;
import com.pps.smartenglish.exercise.repository.StudentAttemptRepository;
import com.pps.smartenglish.placement.service.Bands;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * FR-EXAM-03, D-12: band của lượt thi khóa, tính lúc đọc từ credit đã lưu và bảng quy đổi hiện hành của đề, nên chấm lại
 * hay AI chấm xong thì band tự đúng. Đề không có bảng quy đổi thì không có band (D-167: % mỗi kỹ năng, Viết/Nói suy từ dạng câu).
 */
@Component
public class ExamBands {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** Band tổng của lượt đã nộp gần nhất (D-63). */
    public record Latest(BigDecimal overall, Instant at) {}

    private final ExamBandRuleRepository rules;
    private final QuestionRepository questions;
    private final StudentAnswerRepository answers;
    private final StudentAttemptRepository attempts;
    private final GraderRegistry graders;

    ExamBands(ExamBandRuleRepository rules, QuestionRepository questions, StudentAnswerRepository answers, StudentAttemptRepository attempts, GraderRegistry graders) {
        this.graders = graders;
        this.rules = rules;
        this.questions = questions;
        this.answers = answers;
        this.attempts = attempts;
    }

    /**
     * Lượt phải đã nộp. Null khi đề chưa có bảng quy đổi. Mỗi kỹ năng có % điểm; band chỉ khi kỹ năng có bảng và hết câu đang chấm (D-167).
     */
    AttemptBands of(StudentAttempt attempt) {
        var table = rules.findByBankIdOrderBySkillAscMinPercentAsc(attempt.getBankId());
        if (table.isEmpty()) {
            return null;
        }
        Map<Long, StudentAnswer> byQuestion = new LinkedHashMap<>();
        answers.findByAttemptId(attempt.getId()).forEach(a -> byQuestion.put(a.getQuestionId(), a));
        Map<String, BigDecimal> earned = new LinkedHashMap<>();
        Map<String, Integer> total = new LinkedHashMap<>();
        Set<String> pendingSkills = new HashSet<>();
        for (Question q : questions.findActiveByBankId(attempt.getBankId())) {
            String skill = skillOf(q);
            if (skill == null) {
                continue;
            }
            var a = byQuestion.get(q.getId());
            if (a != null && awaiting(q, a)) {
                pendingSkills.add(skill);
            }
            earned.merge(skill, a == null || a.getCredit() == null ? BigDecimal.ZERO : a.getCredit(), BigDecimal::add);
            total.merge(skill, 1, Integer::sum);
        }
        if (total.isEmpty()) {
            return null;
        }
        Map<String, SkillBand> skills = new LinkedHashMap<>();
        total.forEach((skill, n) -> {
            BigDecimal correct = earned.get(skill).setScale(2, RoundingMode.HALF_UP);
            // tính từ điểm chưa làm tròn; DOWN để 69,996 không làm tròn thành 70 rồi lọt mốc
            BigDecimal percent = earned.get(skill).multiply(HUNDRED).divide(BigDecimal.valueOf(n), 2, RoundingMode.DOWN);
            boolean banded = table.stream().anyMatch(r -> r.getSkill().equals(skill));
            skills.put(skill, new SkillBand(correct, n, percent, banded && !pendingSkills.contains(skill) ? bandFor(table, skill, percent) : null));
        });
        var bandValues = skills.values().stream().map(SkillBand::band).filter(Objects::nonNull).toList();
        boolean bandedPending = table.stream().anyMatch(r -> pendingSkills.contains(r.getSkill()));
        BigDecimal overall = bandValues.isEmpty() || bandedPending ? null : Bands.overall(bandValues);
        return new AttemptBands(skills, overall, !pendingSkills.isEmpty());
    }

    /** D-167: Viết là WRITING, Nói (gồm nghe ghi âm, video phản xạ) là SPEAKING; câu tự chấm theo skill người soạn chọn. */
    private String skillOf(Question q) {
        if (q.getType() == QuestionType.WRITING) {
            return "WRITING";
        }
        if (q.getType().isSpeakingLike()) {
            return "SPEAKING";
        }
        return graders.isAutoGraded(q.getType()) ? q.getSkill() : null;
    }

    /** Viết/Nói chờ credit từ phiên AI; câu tự chấm chờ is_correct (viết lại do AI chấm). */
    private static boolean awaiting(Question q, StudentAnswer a) {
        return ResultAssembler.AI_SESSION_TYPES.contains(q.getType()) ? a.getCredit() == null : a.getCorrect() == null;
    }

    /** Lượt đã nộp gần nhất (đầy đủ kết quả) của đề có bảng quy đổi. */
    public Optional<Latest> latest(long userId) {
        return attempts.findLatestBanded(userId, Pageable.ofSize(1)).stream().findFirst()
                .flatMap(a -> Optional.ofNullable(of(a)).filter(x -> x.overall() != null).map(b -> new Latest(b.overall(), a.getSubmittedAt())));
    }

    private static BigDecimal bandFor(List<ExamBandRule> table, String skill, BigDecimal percent) {
        return table.stream().filter(r -> r.getSkill().equals(skill) && BigDecimal.valueOf(r.getMinPercent()).compareTo(percent) <= 0)
                .max(Comparator.comparingInt(ExamBandRule::getMinPercent)).map(r -> r.getBand().setScale(1, RoundingMode.HALF_UP))
                .orElse(BigDecimal.ZERO.setScale(1));
    }
}

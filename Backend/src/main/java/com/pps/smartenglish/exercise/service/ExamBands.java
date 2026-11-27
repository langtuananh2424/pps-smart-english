package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.domain.ExamBandRule;
import com.pps.smartenglish.exercise.domain.Question;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * FR-EXAM-03, D-12: band của lượt thi khóa, tính lúc đọc từ credit đã lưu và bảng quy đổi hiện hành của đề, nên chấm lại
 * hay AI chấm xong thì band tự đúng. Đề không có bảng quy đổi thì không có band.
 */
@Component
public class ExamBands {

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

    /** Lượt phải đã nộp. Null khi đề chưa có bảng quy đổi. */
    AttemptBands of(StudentAttempt attempt) {
        var table = rules.findByBankIdOrderBySkillAscMinCorrectAsc(attempt.getBankId());
        if (table.isEmpty()) {
            return null;
        }
        Map<Long, StudentAnswer> byQuestion = new LinkedHashMap<>();
        answers.findByAttemptId(attempt.getId()).forEach(a -> byQuestion.put(a.getQuestionId(), a));
        Map<String, BigDecimal> earned = new LinkedHashMap<>();
        Map<String, Integer> total = new LinkedHashMap<>();
        boolean pending = false;
        for (Question q : questions.findActiveByBankId(attempt.getBankId())) {
            if (q.getSkill() == null || !graders.isAutoGraded(q.getType())) { // Viết/Nói chưa vào band (D-164)
                continue;
            }
            var a = byQuestion.get(q.getId());
            pending |= a != null && a.getCorrect() == null;
            earned.merge(q.getSkill(), a == null || a.getCredit() == null ? BigDecimal.ZERO : a.getCredit(), BigDecimal::add);
            total.merge(q.getSkill(), 1, Integer::sum);
        }
        Map<String, SkillBand> skills = new LinkedHashMap<>();
        total.forEach((skill, n) -> {
            if (table.stream().anyMatch(r -> r.getSkill().equals(skill))) {
                BigDecimal correct = earned.get(skill).setScale(2, RoundingMode.HALF_UP);
                skills.put(skill, new SkillBand(correct, n, bandFor(table, skill, correct)));
            }
        });
        if (skills.isEmpty()) {
            return null;
        }
        return new AttemptBands(skills, Bands.overall(skills.values().stream().map(SkillBand::band).toList()), pending);
    }

    /** Lượt đã nộp gần nhất (đầy đủ kết quả) của đề có bảng quy đổi. */
    public Optional<Latest> latest(long userId) {
        return attempts.findLatestBanded(userId, Pageable.ofSize(1)).stream().findFirst()
                .flatMap(a -> Optional.ofNullable(of(a)).map(b -> new Latest(b.overall(), a.getSubmittedAt())));
    }

    private static BigDecimal bandFor(List<ExamBandRule> table, String skill, BigDecimal correct) {
        return table.stream().filter(r -> r.getSkill().equals(skill) && BigDecimal.valueOf(r.getMinCorrect()).compareTo(correct) <= 0)
                .max(Comparator.comparingInt(ExamBandRule::getMinCorrect)).map(r -> r.getBand().setScale(1, RoundingMode.HALF_UP))
                .orElse(BigDecimal.ZERO.setScale(1));
    }
}

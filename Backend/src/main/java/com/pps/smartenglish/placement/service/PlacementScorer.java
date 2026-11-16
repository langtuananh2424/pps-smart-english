package com.pps.smartenglish.placement.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.Question;
import com.pps.smartenglish.exercise.grading.GraderRegistry;
import com.pps.smartenglish.exercise.service.QuestionSet;
import com.pps.smartenglish.placement.dto.PlacementDtos.SkillResult;
import com.pps.smartenglish.placement.repository.PlacementBandRuleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Chấm bài đầu vào trong bộ nhớ bằng các grader sẵn có, gom theo kỹ năng rồi quy đổi band. */
@Component
class PlacementScorer {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    record Scored(Map<String, SkillResult> skills, BigDecimal overall) {}

    private final GraderRegistry graders;
    private final PlacementBandRuleRepository rules;

    PlacementScorer(GraderRegistry graders, PlacementBandRuleRepository rules) {
        this.graders = graders;
        this.rules = rules;
    }

    /** Câu bỏ trống hoặc sai định dạng tính 0. Câu không có skill (dữ liệu lạ) bị bỏ qua. */
    Scored score(QuestionSet set, Map<Long, JsonNode> given) {
        Map<String, BigDecimal> earned = new LinkedHashMap<>();
        Map<String, Integer> total = new LinkedHashMap<>();
        for (Question q : set.questions()) {
            if (q.getSkill() == null) {
                continue;
            }
            BigDecimal credit = graders.credit(q.getType(), set.inputOf(q), given.get(q.getId()));
            earned.merge(q.getSkill(), credit == null ? BigDecimal.ZERO : credit, BigDecimal::add);
            total.merge(q.getSkill(), 1, Integer::sum);
        }
        var bandRules = rules.findAllByOrderBySkillAscMinPercentAsc();
        Map<String, SkillResult> out = new LinkedHashMap<>();
        total.forEach((skill, n) -> {
            BigDecimal correct = earned.get(skill).setScale(2, RoundingMode.HALF_UP);
            BigDecimal percent = correct.multiply(HUNDRED).divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
            out.put(skill, new SkillResult(correct, n, percent, Bands.bandFor(bandRules, skill, percent)));
        });
        return new Scored(out, Bands.overall(out.values().stream().map(SkillResult::band).toList()));
    }
}

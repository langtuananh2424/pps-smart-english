package com.pps.smartenglish.placement.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pps.smartenglish.placement.domain.PlacementAttempt;
import com.pps.smartenglish.placement.dto.PlacementDtos.MyPlacement;
import com.pps.smartenglish.placement.dto.PlacementDtos.Recommendation;
import com.pps.smartenglish.placement.dto.PlacementDtos.Result;
import com.pps.smartenglish.placement.dto.PlacementDtos.SkillResult;
import com.pps.smartenglish.placement.repository.PlacementAttemptRepository;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-TEST-02: kết quả đã lưu và khóa gợi ý (FR-GOAL-03). Gợi ý tính lúc đọc để theo dải band hiện hành của khóa. */
@Service
public class PlacementResultService {

    private static final TypeReference<Map<String, SkillResult>> SKILLS = new TypeReference<>() {};

    private final PlacementAttemptRepository attempts;
    private final CourseRecommender recommender;
    private final ObjectMapper mapper;

    PlacementResultService(PlacementAttemptRepository attempts, CourseRecommender recommender, ObjectMapper mapper) {
        this.attempts = attempts;
        this.recommender = recommender;
        this.mapper = mapper;
    }

    /** Lượt phải đã nộp. */
    Result of(PlacementAttempt a) {
        return new Result(a.getId(), a.getSubmittedAt(), mapper.convertValue(a.getSkillResults(), SKILLS), a.getOverallBand(),
                recommender.recommend(a.getOverallBand()).orElse(null));
    }

    @Transactional(readOnly = true)
    public MyPlacement mine(long userId) {
        return new MyPlacement(latest(userId).map(this::of).orElse(null));
    }

    @Transactional(readOnly = true)
    public Recommendation recommendation(long userId) {
        var a = latest(userId);
        if (a.isEmpty()) {
            return new Recommendation(null, "NO_PLACEMENT", null);
        }
        var band = a.get().getOverallBand();
        var course = recommender.recommend(band);
        return new Recommendation(course.orElse(null), course.isPresent() ? null : "NO_MATCHING_COURSE", band);
    }

    private Optional<PlacementAttempt> latest(long userId) {
        return attempts.findFirstByUserIdAndSubmittedAtIsNotNullOrderBySubmittedAtDescIdDesc(userId);
    }
}

package com.pps.smartenglish.suggestion.service;

import com.pps.smartenglish.catalog.domain.Course;
import com.pps.smartenglish.catalog.service.CourseStatsService;
import com.pps.smartenglish.catalog.service.CourseStatsService.Stats;
import com.pps.smartenglish.placement.service.CourseRecommender;
import com.pps.smartenglish.profile.dto.QaDtos.QaAnswers;
import com.pps.smartenglish.profile.dto.QaDtos.QaRequest;
import com.pps.smartenglish.profile.service.QaService;
import com.pps.smartenglish.shared.ratelimit.RateLimiter;
import com.pps.smartenglish.suggestion.config.SuggestionProperties;
import com.pps.smartenglish.suggestion.dto.SuggestionDtos.Basis;
import com.pps.smartenglish.suggestion.dto.SuggestionDtos.CourseCard;
import com.pps.smartenglish.suggestion.dto.SuggestionDtos.CourseSuggestion;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-GOAL-03, D-56: khóa chính và lộ trình các khóa kế tiếp tới band mục tiêu. */
@Service
public class CourseSuggestionService {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final List<BandSource> sources;
    private final CourseRecommender recommender;
    private final CourseStatsService stats;
    private final QaService qa;
    private final SuggestionProperties props;
    private final RateLimiter limiter;

    CourseSuggestionService(List<BandSource> sources, CourseRecommender recommender, CourseStatsService stats, QaService qa,
                            SuggestionProperties props, RateLimiter limiter) {
        this.sources = sources;
        this.recommender = recommender;
        this.stats = stats;
        this.qa = qa;
        this.props = props;
        this.limiter = limiter;
    }

    /** Band từ nguồn đầu tiên có dữ liệu (placement trước, tự đánh giá sau); target từ Q&A đã lưu. */
    @Transactional(readOnly = true)
    public CourseSuggestion forUser(long userId) {
        BigDecimal target = qa.answers(userId).map(QaAnswers::targetBand).orElse(null);
        return sources.stream().map(s -> s.read(userId)).flatMap(Optional::stream).findFirst()
                .map(r -> build(r.basis(), r.band(), target))
                .orElseGet(() -> new CourseSuggestion(Basis.NONE, null, target, null, List.of()));
    }

    /** Khách làm Q&A: tính từ câu trả lời, không lưu gì. Giới hạn theo IP vì endpoint công khai. */
    @Transactional(readOnly = true)
    public CourseSuggestion forGuest(QaRequest req, String ip) {
        limiter.requireHit("suggestion:ip:" + ip, props.guestIpLimit(), WINDOW, "suggestion:ip:" + ip);
        QaAnswers a = QaService.normalize(req);
        return build(Basis.SELF_LEVEL, props.bandOf(a.selfLevel()), a.targetBand());
    }

    private CourseSuggestion build(Basis basis, BigDecimal band, BigDecimal target) {
        var plan = recommender.plan(band, target);
        if (plan.isEmpty()) {
            return new CourseSuggestion(basis, band, target, null, List.of());
        }
        List<Course> all = new ArrayList<>(List.of(plan.get().primary()));
        all.addAll(plan.get().path());
        var byCourse = stats.of(all.stream().map(Course::getId).toList());
        List<CourseCard> cards = all.stream().map(c -> card(c, CourseStatsService.of(c.getId(), byCourse))).toList();
        return new CourseSuggestion(basis, band, target, cards.get(0), cards.subList(1, cards.size()));
    }

    private static CourseCard card(Course c, Stats s) {
        return new CourseCard(c.getId(), c.getSlug(), c.getTitle(), c.getBandFrom(), c.getBandTo(), s.trialLessonCount(),
                s.priceFromVnd());
    }
}

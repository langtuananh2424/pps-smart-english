package com.pps.smartenglish.catalog.service;

import com.pps.smartenglish.catalog.domain.CourseLevel;
import com.pps.smartenglish.catalog.domain.Topic;
import com.pps.smartenglish.catalog.dto.ProductOption;
import com.pps.smartenglish.catalog.dto.TopicLessonCount;
import com.pps.smartenglish.catalog.repository.CourseLevelRepository;
import com.pps.smartenglish.catalog.repository.LessonRepository;
import com.pps.smartenglish.catalog.repository.TopicRepository;
import com.pps.smartenglish.commerce.domain.Product;
import com.pps.smartenglish.commerce.repository.ProductRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Số liệu hiển thị của thẻ khóa (level, bài, bài học thử, giá, gói bán) cho nhiều khóa cùng lúc: 5 truy vấn cố định,
 * không N+1. Dùng chung cho danh mục công khai và gợi ý khóa. Gọi trong transaction của caller.
 */
@Component
public class CourseStatsService {

    private static final String PUBLISHED = "PUBLISHED";

    public record Stats(int levelCount, int lessonCount, int trialLessonCount, Long priceFromVnd, List<ProductOption> products) {
        static final Stats EMPTY = new Stats(0, 0, 0, null, List.of());
    }

    private final CourseLevelRepository levels;
    private final TopicRepository topics;
    private final LessonRepository lessons;
    private final ProductRepository products;

    CourseStatsService(CourseLevelRepository levels, TopicRepository topics, LessonRepository lessons, ProductRepository products) {
        this.levels = levels;
        this.topics = topics;
        this.lessons = lessons;
        this.products = products;
    }

    /** Khóa không có level PUBLISHED nào thì trả số 0, không có giá. */
    public static Stats of(Long courseId, Map<Long, Stats> all) {
        return all.getOrDefault(courseId, Stats.EMPTY);
    }

    public Map<Long, Stats> of(Collection<Long> courseIds) {
        if (courseIds.isEmpty()) {
            return Map.of();
        }
        // chỉ tính level, topic, bài PUBLISHED: cùng quy tắc với cây khóa học công khai
        List<CourseLevel> lvls = levels.findByCourseIdInAndStatus(courseIds, PUBLISHED);
        Map<Long, Long> courseOfLevel = lvls.stream().collect(Collectors.toMap(CourseLevel::getId, CourseLevel::getCourseId));
        List<Topic> tps = lvls.isEmpty() ? List.of()
                : topics.findByLevelIdInAndStatusOrderBySortOrderAscIdAsc(courseOfLevel.keySet(), PUBLISHED);
        Map<Long, Long> lessonsByTopic = tps.isEmpty() ? Map.of()
                : lessons.countPublishedByTopic(tps.stream().map(Topic::getId).toList()).stream()
                        .collect(Collectors.toMap(TopicLessonCount::topicId, TopicLessonCount::lessons));
        List<Product> sellable = products.findActiveLevelProducts();

        Map<Long, int[]> counts = new HashMap<>(); // [levels, lessons, trialLessons]
        lvls.forEach(l -> counts.computeIfAbsent(l.getCourseId(), k -> new int[3])[0]++);
        for (Topic t : tps) {
            int n = lessonsByTopic.getOrDefault(t.getId(), 0L).intValue();
            int[] c = counts.get(courseOfLevel.get(t.getLevelId()));
            c[1] += n;
            if (t.isFreeTrial()) {
                c[2] += n;
            }
        }
        Map<Long, List<ProductOption>> byCourse = new HashMap<>();
        for (Product p : sellable) {
            p.getLevelIds().stream().map(courseOfLevel::get).filter(Objects::nonNull).distinct()
                    .forEach(cid -> byCourse.computeIfAbsent(cid, k -> new ArrayList<>())
                            .add(new ProductOption(p.getId(), p.getName(), p.getPriceVnd(), p.getAccessDays())));
        }
        Map<Long, Stats> out = new HashMap<>();
        counts.forEach((cid, c) -> {
            List<ProductOption> opts = byCourse.getOrDefault(cid, List.of()).stream()
                    .sorted(Comparator.comparing(ProductOption::accessDays, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(ProductOption::id))
                    .toList();
            Long from = opts.stream().map(ProductOption::priceVnd).min(Long::compare).orElse(null);
            out.put(cid, new Stats(c[0], c[1], c[2], from, opts));
        });
        return out;
    }
}

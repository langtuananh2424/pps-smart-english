package com.pps.smartenglish.placement.service;

import com.pps.smartenglish.catalog.domain.ContentStatus;
import com.pps.smartenglish.catalog.domain.Course;
import com.pps.smartenglish.catalog.repository.CourseRepository;
import com.pps.smartenglish.placement.dto.PlacementDtos.CourseRef;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * FR-GOAL-03, D-56: khóa PUBLISHED có band_from <= band < band_to. Band vượt mọi dải thì gợi khóa có band_to lớn nhất;
 * không khóa nào khớp thì rỗng. Nguồn band (placement hay trình độ tự đánh giá) do caller quyết định.
 */
@Component
public class CourseRecommender {

    /** primary là khóa phù hợp; path là các khóa kế tiếp sau primary, tới khóa có band_to >= band mục tiêu. */
    public record Plan(Course primary, List<Course> path) {}

    private final CourseRepository courses;

    CourseRecommender(CourseRepository courses) {
        this.courses = courses;
    }

    public Optional<CourseRef> recommend(BigDecimal band) {
        return plan(band, null).map(p -> new CourseRef(p.primary().getId(), p.primary().getSlug(), p.primary().getTitle()));
    }

    /** targetBand null hoặc đã nằm trong dải của primary thì path rỗng. */
    public Optional<Plan> plan(BigDecimal band, BigDecimal targetBand) {
        List<Course> ranged = courses.findByStatusAndBandFromIsNotNullOrderByBandFromAscIdAsc(ContentStatus.PUBLISHED);
        return ranged.stream().filter(c -> c.getBandFrom().compareTo(band) <= 0 && band.compareTo(c.getBandTo()) < 0).findFirst()
                .or(() -> ranged.stream().max(Comparator.comparing(Course::getBandTo)).filter(c -> band.compareTo(c.getBandTo()) >= 0))
                .map(primary -> new Plan(primary, pathAfter(ranged, primary, targetBand)));
    }

    private static List<Course> pathAfter(List<Course> ranged, Course primary, BigDecimal target) {
        List<Course> path = new ArrayList<>();
        if (target == null) {
            return path;
        }
        Course last = primary;
        for (Course c : ranged.subList(ranged.indexOf(primary) + 1, ranged.size())) {
            if (last.getBandTo().compareTo(target) >= 0) {
                break;
            }
            path.add(c);
            last = c;
        }
        return path;
    }
}

package com.pps.smartenglish.placement.service;

import com.pps.smartenglish.catalog.domain.ContentStatus;
import com.pps.smartenglish.catalog.domain.Course;
import com.pps.smartenglish.catalog.repository.CourseRepository;
import com.pps.smartenglish.placement.dto.PlacementDtos.CourseRef;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * FR-GOAL-03: khóa PUBLISHED có band_from <= band < band_to. Band vượt mọi dải thì gợi khóa có band_to lớn nhất;
 * không khóa nào khớp thì rỗng. Chưa có quy tắc quy đổi self_level sang khóa nên không dùng self_level.
 */
@Component
public class CourseRecommender {

    private final CourseRepository courses;

    CourseRecommender(CourseRepository courses) {
        this.courses = courses;
    }

    public Optional<CourseRef> recommend(BigDecimal band) {
        List<Course> ranged = courses.findByStatusAndBandFromIsNotNullOrderByBandFromAscIdAsc(ContentStatus.PUBLISHED);
        return ranged.stream().filter(c -> c.getBandFrom().compareTo(band) <= 0 && band.compareTo(c.getBandTo()) < 0).findFirst()
                .or(() -> ranged.stream().max(Comparator.comparing(Course::getBandTo)).filter(c -> band.compareTo(c.getBandTo()) >= 0))
                .map(c -> new CourseRef(c.getId(), c.getSlug(), c.getTitle()));
    }
}

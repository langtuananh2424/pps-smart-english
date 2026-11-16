package com.pps.smartenglish.catalog.repository;

import com.pps.smartenglish.catalog.domain.Topic;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    List<Topic> findByLevelIdInOrderBySortOrderAscIdAsc(Collection<Long> levelIds);

    /** Đường đọc của học viên: chỉ topic đã PUBLISHED. */
    List<Topic> findByLevelIdInAndStatusOrderBySortOrderAscIdAsc(Collection<Long> levelIds, String status);

    /** Topic học thử của khóa (D-57: tối đa một). */
    @Query("select t from Topic t, CourseLevel lv where t.levelId = lv.id and lv.courseId = :courseId and t.freeTrial = true")
    List<Topic> findFreeTrialByCourse(@Param("courseId") Long courseId);

    boolean existsByLevelId(Long levelId);
}

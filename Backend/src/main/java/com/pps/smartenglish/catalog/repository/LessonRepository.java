package com.pps.smartenglish.catalog.repository;

import com.pps.smartenglish.catalog.domain.Lesson;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    List<Lesson> findByTopicIdInAndStatusOrderBySortOrderAscIdAsc(Collection<Long> topicIds, String status);

    /** CMS: mọi trạng thái. */
    List<Lesson> findByTopicIdInOrderBySortOrderAscIdAsc(Collection<Long> topicIds);

    boolean existsByTopicId(Long topicId);
}

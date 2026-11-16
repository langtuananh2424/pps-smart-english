package com.pps.smartenglish.catalog.repository;

import com.pps.smartenglish.catalog.domain.Lesson;
import com.pps.smartenglish.catalog.dto.TopicLessonCount;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    List<Lesson> findByTopicIdInAndStatusOrderBySortOrderAscIdAsc(Collection<Long> topicIds, String status);

    /** Số bài PUBLISHED theo topic, một truy vấn gom nhóm. */
    @Query("select new com.pps.smartenglish.catalog.dto.TopicLessonCount(l.topicId, count(l)) from Lesson l "
            + "where l.topicId in :topicIds and l.status = 'PUBLISHED' group by l.topicId")
    List<TopicLessonCount> countPublishedByTopic(@Param("topicIds") Collection<Long> topicIds);

    /** CMS: mọi trạng thái. */
    List<Lesson> findByTopicIdInOrderBySortOrderAscIdAsc(Collection<Long> topicIds);

    boolean existsByTopicId(Long topicId);
}

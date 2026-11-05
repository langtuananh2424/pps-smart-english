package com.pps.smartenglish.lms.repository;

import com.pps.smartenglish.lms.domain.LessonMedia;
import com.pps.smartenglish.lms.dto.PlayableLesson;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LessonMediaRepository extends JpaRepository<LessonMedia, Long> {

    /** Bài và khóa cha đều phải PUBLISHED. */
    @Query("""
            select new com.pps.smartenglish.lms.dto.PlayableLesson(m.id, m.title, m.type, m.durationSec,
                   m.videoSource, m.videoRef, t.freeTrial, lv.id)
            from LessonMedia m, Topic t, CourseLevel lv, Course c
            where m.id = :id and m.status = 'PUBLISHED'
              and t.id = m.topicId and lv.id = t.levelId and c.id = lv.courseId and c.status = 'PUBLISHED'
            """)
    Optional<PlayableLesson> findPlayable(@Param("id") Long id);
}

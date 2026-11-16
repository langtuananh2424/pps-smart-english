package com.pps.smartenglish.home.repository;

import com.pps.smartenglish.commerce.domain.Enrollment;
import com.pps.smartenglish.home.dto.ActivityRow;
import com.pps.smartenglish.home.dto.CourseRow;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Truy vấn đọc ghép nhiều bảng cho Trang chủ; không gắn với một aggregate nên chỉ mượn Enrollment làm kiểu gốc. */
public interface HomeReadRepository extends Repository<Enrollment, Long> {

    @Query("""
            select new com.pps.smartenglish.home.dto.CourseRow(c.id, c.slug, c.title, c.targetType, e.status, e.expiresAt)
            from Enrollment e, Course c
            where e.userId = :userId and c.id = e.courseId and c.status = 'PUBLISHED'
            order by c.id
            """)
    List<CourseRow> myCourses(@Param("userId") Long userId);

    /** Khóa còn hạn sẽ hết trong (now, until], gần hạn nhất trước. */
    @Query("""
            select new com.pps.smartenglish.home.dto.CourseRow(c.id, c.slug, c.title, c.targetType, e.status, e.expiresAt)
            from Enrollment e, Course c
            where e.userId = :userId and c.id = e.courseId and c.status = 'PUBLISHED' and e.status = 'ACTIVE'
              and e.expiresAt > :now and e.expiresAt <= :until
            order by e.expiresAt, c.id
            """)
    List<CourseRow> expiring(@Param("userId") Long userId, @Param("now") Instant now, @Param("until") Instant until);

    @Query("""
            select new com.pps.smartenglish.home.dto.ActivityRow(lv.courseId, max(p.updatedAt))
            from LessonProgress p, Lesson l, Topic t, CourseLevel lv
            where p.userId = :userId and l.id = p.lessonId and t.id = l.topicId and lv.id = t.levelId
              and lv.courseId in :courseIds
            group by lv.courseId
            """)
    List<ActivityRow> lastLessonActivity(@Param("userId") Long userId, @Param("courseIds") Collection<Long> courseIds);

    @Query("""
            select new com.pps.smartenglish.home.dto.ActivityRow(lv.courseId, max(coalesce(a.submittedAt, a.startedAt)))
            from StudentAttempt a, QuestionBank b, Topic t, CourseLevel lv
            where a.userId = :userId and b.id = a.bankId and t.id = b.topicId and lv.id = t.levelId
              and lv.courseId in :courseIds
            group by lv.courseId
            """)
    List<ActivityRow> lastAttemptActivity(@Param("userId") Long userId, @Param("courseIds") Collection<Long> courseIds);
}

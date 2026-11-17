package com.pps.smartenglish.home.repository;

import com.pps.smartenglish.commerce.domain.Enrollment;
import com.pps.smartenglish.home.dto.ActivityRow;
import com.pps.smartenglish.home.dto.CountRow;
import com.pps.smartenglish.home.dto.CourseRow;
import com.pps.smartenglish.home.dto.TypeStatRow;
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

    /** D-61: khóa chưa có enrollment nào của người dùng nhưng có tiến độ ở topic học thử. */
    @Query("""
            select new com.pps.smartenglish.home.dto.CourseRow(c.id, c.slug, c.title, c.targetType)
            from Course c
            where c.status = 'PUBLISHED'
              and not exists (select 1 from Enrollment e where e.userId = :userId and e.courseId = c.id)
              and exists (select 1 from LessonProgress p, Lesson l, Topic t, CourseLevel lv
                          where p.userId = :userId and l.id = p.lessonId and t.id = l.topicId and t.freeTrial = true
                            and lv.id = t.levelId and lv.courseId = c.id)
            order by c.id
            """)
    List<CourseRow> trialCourses(@Param("userId") Long userId);

    /** Bài học viên thấy được (bài, topic, level đều PUBLISHED) theo khóa. */
    @Query("""
            select new com.pps.smartenglish.home.dto.CountRow(lv.courseId, count(l))
            from Lesson l, Topic t, CourseLevel lv
            where lv.courseId in :courseIds and lv.status = 'PUBLISHED' and t.levelId = lv.id and t.status = 'PUBLISHED'
              and l.topicId = t.id and l.status = 'PUBLISHED'
            group by lv.courseId
            """)
    List<CountRow> visibleLessonCounts(@Param("courseIds") Collection<Long> courseIds);

    @Query("""
            select new com.pps.smartenglish.home.dto.CountRow(lv.courseId, count(p))
            from LessonProgress p, Lesson l, Topic t, CourseLevel lv
            where p.userId = :userId and p.completedAt is not null and l.id = p.lessonId and l.status = 'PUBLISHED'
              and t.id = l.topicId and t.status = 'PUBLISHED' and lv.id = t.levelId and lv.status = 'PUBLISHED'
              and lv.courseId in :courseIds
            group by lv.courseId
            """)
    List<CountRow> completedLessonCounts(@Param("userId") Long userId, @Param("courseIds") Collection<Long> courseIds);

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

    /** D-62: câu đã nộp và câu đúng theo khóa, trong bộ PRACTICE của khóa, mọi lượt đã nộp. */
    @Query("""
            select new com.pps.smartenglish.home.dto.TypeStatRow(lv.courseId, q.type, count(a), count(case when a.correct = true then 1 end))
            from StudentAnswer a, StudentAttempt t, Question q, QuestionBank b, Topic tp, CourseLevel lv
            where t.id = a.attemptId and t.userId = :userId and t.submittedAt is not null and q.id = a.questionId
              and b.id = q.bankId and b.mode = 'PRACTICE' and b.purpose = 'COURSE' and tp.id = b.topicId
              and lv.id = tp.levelId and lv.courseId in :courseIds
            group by lv.courseId, q.type
            """)
    List<TypeStatRow> answerStats(@Param("userId") Long userId, @Param("courseIds") Collection<Long> courseIds);

    /** Số câu đang hiển thị (bộ, topic, level đều PUBLISHED) theo dạng; cột correct luôn 0. */
    @Query("""
            select new com.pps.smartenglish.home.dto.TypeStatRow(lv.courseId, q.type, count(q), 0L)
            from Question q, QuestionBank b, Topic tp, CourseLevel lv
            where b.id = q.bankId and b.mode = 'PRACTICE' and b.purpose = 'COURSE' and b.status = 'PUBLISHED'
              and tp.id = b.topicId and tp.status = 'PUBLISHED' and lv.id = tp.levelId and lv.status = 'PUBLISHED'
              and lv.courseId = :courseId
            group by lv.courseId, q.type
            """)
    List<TypeStatRow> publishedQuestionCounts(@Param("courseId") Long courseId);
}

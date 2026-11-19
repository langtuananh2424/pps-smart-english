package com.pps.smartenglish.exercise.repository;

import com.pps.smartenglish.exercise.domain.QuestionBank;
import com.pps.smartenglish.exercise.dto.BankInfo;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionBankRepository extends JpaRepository<QuestionBank, Long> {

    /** Khóa dòng bộ câu hỏi để đếm + chèn hỏi đáp của cùng một bộ chạy tuần tự. Rỗng nếu không có bộ. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b.id from QuestionBank b where b.id = :id")
    List<Long> lockById(@Param("id") Long id);

    /** CMS: mọi trạng thái. */
    List<QuestionBank> findByTopicIdOrderByIdAsc(Long topicId);

    List<QuestionBank> findByPurposeOrderByIdAsc(String purpose);

    /** Bộ của khóa học (không PLACEMENT) theo topic và trạng thái; người gọi tự lọc theo mode. */
    List<QuestionBank> findByTopicIdInAndStatusAndPurposeOrderByIdAsc(Collection<Long> topicIds, String status, String purpose);

    /** Đề kiểm tra đầu vào đang hoạt động: PLACEMENT + PUBLISHED, mới nhất trước. */
    List<QuestionBank> findByPurposeAndStatusOrderByIdDesc(String purpose, String status);

    @Query("""
            select new com.pps.smartenglish.exercise.dto.BankInfo(b.id, b.title, b.lessonId, b.trial, t.freeTrial, lv.id,
                   b.timeLimitSec, b.mode, t.id)
            from QuestionBank b, Topic t, CourseLevel lv, Course c
            where b.id = :id and b.status = 'PUBLISHED'
              and t.id = b.topicId and t.status = 'PUBLISHED'
              and lv.id = t.levelId and lv.status = 'PUBLISHED'
              and c.id = lv.courseId and c.status = 'PUBLISHED'
              and (b.lessonId is null or exists (select 1 from Lesson l where l.id = b.lessonId and l.status = 'PUBLISHED'))
            """)
    Optional<BankInfo> findInfo(@Param("id") Long id);

    /** Tên khóa học chứa bộ câu hỏi, đưa vào prompt chấm viết để model chấm theo mục tiêu của khóa. */
    @Query("""
            select c.title from QuestionBank b, Topic t, CourseLevel lv, Course c
            where b.id = :id and t.id = b.topicId and lv.id = t.levelId and c.id = lv.courseId
            """)
    Optional<String> findCourseTitle(@Param("id") Long id);

    /** Khóa chứa bộ câu hỏi, để chọn lượt AI định kỳ của khóa đó (D-47). */
    @Query("""
            select c.id from QuestionBank b, Topic t, CourseLevel lv, Course c
            where b.id = :id and t.id = b.topicId and lv.id = t.levelId and c.id = lv.courseId
            """)
    Optional<Long> findCourseId(@Param("id") Long id);
}

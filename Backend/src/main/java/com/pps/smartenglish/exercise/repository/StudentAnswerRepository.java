package com.pps.smartenglish.exercise.repository;

import com.pps.smartenglish.exercise.domain.StudentAnswer;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentAnswerRepository extends JpaRepository<StudentAnswer, Long> {

    List<StudentAnswer> findByAttemptId(Long attemptId);

    Optional<StudentAnswer> findByAttemptIdAndQuestionId(Long attemptId, Long questionId);

    /** CMS: câu đã có bài nộp thì không đổi type và không bỏ lựa chọn đang có; sửa đáp án thì phải chấm lại. */
    boolean existsByQuestionId(Long questionId);

    /** Trong các id đã cho, câu nào đã có bài nộp (cho regradeRequired). */
    @Query("select distinct a.questionId from StudentAnswer a where a.questionId in :ids")
    List<Long> findQuestionIdsWithAnswers(@Param("ids") Collection<Long> ids);

    /** Câu trả lời của lượt ĐÃ nộp cho một câu: số câu trả lời bị ảnh hưởng khi chấm lại. */
    @Query("select count(a) from StudentAnswer a, StudentAttempt t where a.questionId = :questionId and t.id = a.attemptId "
            + "and t.submittedAt is not null")
    long countSubmitted(@Param("questionId") Long questionId);

    /** Số lượt làm bài (đã nộp) bị ảnh hưởng. */
    @Query("select count(distinct a.attemptId) from StudentAnswer a, StudentAttempt t where a.questionId = :questionId "
            + "and t.id = a.attemptId and t.submittedAt is not null")
    long countSubmittedAttempts(@Param("questionId") Long questionId);

    /**
     * Cận trên số lời gọi AI khi chấm lại câu viết lại: câu trả lời có text khác rỗng. Ép json về chuỗi để đếm giống nhau
     * trên H2 và PostgreSQL (jsonb in dấu cách sau ':'). Cận trên vì luật còn quyết được nhiều câu mà không cần AI.
     */
    @Query(value = "select count(*) from student_answers a join student_exercise_attempts t on t.id = a.attempt_id "
            + "where a.question_id = :questionId and t.submitted_at is not null and a.answer is not null "
            + "and cast(a.answer as varchar(100000)) not like '%\"text\":\"\"%' "
            + "and cast(a.answer as varchar(100000)) not like '%\"text\": \"\"%'", nativeQuery = true)
    long countWithText(@Param("questionId") Long questionId);

    /** Ghi kết quả chấm lại: đổi is_correct sang bất kỳ giá trị nào, kể cả đang null. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update StudentAnswer a set a.correct = :correct where a.id = :id")
    int setVerdict(@Param("id") Long id, @Param("correct") boolean correct);

    /** Ghi kết quả AI một lần: chỉ khi còn null nên chạy lại cùng job không ghi đè hay tính điểm hai lần. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update StudentAnswer a set a.correct = :correct where a.attemptId = :attemptId and a.questionId = :questionId "
            + "and a.correct is null")
    int applyAiVerdict(@Param("attemptId") Long attemptId, @Param("questionId") Long questionId, @Param("correct") boolean correct);
}

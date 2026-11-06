package com.pps.smartenglish.exercise.repository;

import com.pps.smartenglish.exercise.domain.StudentAnswer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentAnswerRepository extends JpaRepository<StudentAnswer, Long> {

    List<StudentAnswer> findByAttemptId(Long attemptId);

    Optional<StudentAnswer> findByAttemptIdAndQuestionId(Long attemptId, Long questionId);

    /** Ghi kết quả AI một lần: chỉ khi còn null nên chạy lại cùng job không ghi đè hay tính điểm hai lần. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update StudentAnswer a set a.correct = :correct where a.attemptId = :attemptId and a.questionId = :questionId "
            + "and a.correct is null")
    int applyAiVerdict(@Param("attemptId") Long attemptId, @Param("questionId") Long questionId, @Param("correct") boolean correct);
}

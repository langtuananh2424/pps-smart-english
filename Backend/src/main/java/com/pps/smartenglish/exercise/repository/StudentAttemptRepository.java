package com.pps.smartenglish.exercise.repository;

import com.pps.smartenglish.exercise.domain.StudentAttempt;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentAttemptRepository extends JpaRepository<StudentAttempt, Long> {

    /** Luôn kèm userId: attempt của người khác coi như không tồn tại. */
    Optional<StudentAttempt> findByIdAndUserId(Long id, Long userId);

    @Query("select a from StudentAttempt a where a.userId = :userId and a.id < :before order by a.id desc")
    List<StudentAttempt> findPage(@Param("userId") Long userId, @Param("before") Long before, Pageable page);

    /** Nộp bài nguyên tử: chỉ một trong các request đồng thời thấy submitted_at IS NULL; 0 dòng = đã nộp rồi. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update StudentAttempt a set a.submittedAt = :now, a.score = :score, a.maxScore = :maxScore "
            + "where a.id = :id and a.submittedAt is null")
    int claimSubmission(@Param("id") Long id, @Param("now") Instant now, @Param("score") int score,
                        @Param("maxScore") int maxScore);

    /** Cộng điểm do AI chấm sau khi nộp, nguyên tử để nhiều kết quả không ghi đè nhau. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update StudentAttempt a set a.score = a.score + :points where a.id = :id and a.submittedAt is not null")
    int addScore(@Param("id") Long id, @Param("points") int points);
}

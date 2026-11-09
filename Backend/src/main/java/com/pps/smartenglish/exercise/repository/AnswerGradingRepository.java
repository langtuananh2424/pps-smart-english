package com.pps.smartenglish.exercise.repository;

import com.pps.smartenglish.exercise.domain.AnswerGrading;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnswerGradingRepository extends JpaRepository<AnswerGrading, Long> {

    List<AnswerGrading> findByAnswerIdIn(Collection<Long> answerIds);

    /** Chấm lại: bỏ kết quả cũ (đã lưu vào regrade_items.old_result) trước khi ghi kết quả mới. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from AnswerGrading g where g.answerId = :answerId")
    int deleteByAnswerId(@Param("answerId") Long answerId);
}

package com.pps.smartenglish.exercise.repository;

import com.pps.smartenglish.exercise.domain.QuestionHint;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionHintRepository extends JpaRepository<QuestionHint, Long> {

    List<QuestionHint> findByQuestionIdOrderBySortOrderAscIdAsc(Long questionId);

    /** Một truy vấn cho cả bank, tránh N+1. */
    List<QuestionHint> findByQuestionIdInOrderBySortOrderAscIdAsc(Collection<Long> questionIds);

    long countByQuestionId(Long questionId);
}

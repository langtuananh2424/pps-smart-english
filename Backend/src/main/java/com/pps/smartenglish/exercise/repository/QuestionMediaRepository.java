package com.pps.smartenglish.exercise.repository;

import com.pps.smartenglish.exercise.domain.QuestionMedia;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface QuestionMediaRepository extends JpaRepository<QuestionMedia, Long> {

    /** Một truy vấn cho cả bank, tránh N+1. */
    List<QuestionMedia> findByQuestionIdInOrderByQuestionIdAscSortOrderAscIdAsc(Collection<Long> questionIds);

    @Modifying
    @Query("delete from QuestionMedia m where m.questionId = :questionId")
    void deleteByQuestionId(Long questionId);
}

package com.pps.smartenglish.exercise.repository;

import com.pps.smartenglish.exercise.domain.QuestionChoice;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionChoiceRepository extends JpaRepository<QuestionChoice, Long> {

    List<QuestionChoice> findByQuestionIdInOrderByLabelAscIdAsc(Collection<Long> questionIds);

    void deleteByQuestionId(Long questionId);
}

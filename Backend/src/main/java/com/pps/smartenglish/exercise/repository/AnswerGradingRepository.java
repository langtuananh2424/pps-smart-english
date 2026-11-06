package com.pps.smartenglish.exercise.repository;

import com.pps.smartenglish.exercise.domain.AnswerGrading;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerGradingRepository extends JpaRepository<AnswerGrading, Long> {

    List<AnswerGrading> findByAnswerIdIn(Collection<Long> answerIds);
}

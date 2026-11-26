package com.pps.smartenglish.exercise.repository;

import com.pps.smartenglish.exercise.domain.QuestionCheck;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionCheckRepository extends JpaRepository<QuestionCheck, Long> {

    List<QuestionCheck> findByAttemptId(Long attemptId);

    Optional<QuestionCheck> findByAttemptIdAndQuestionId(Long attemptId, Long questionId);
}

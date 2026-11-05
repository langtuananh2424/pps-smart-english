package com.pps.smartenglish.exercise.repository;

import com.pps.smartenglish.exercise.domain.StudentAnswer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentAnswerRepository extends JpaRepository<StudentAnswer, Long> {

    List<StudentAnswer> findByAttemptId(Long attemptId);
}

package com.pps.smartenglish.exercise.repository;

import com.pps.smartenglish.exercise.domain.Question;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByBankIdOrderBySortOrderAscIdAsc(Long bankId);
}

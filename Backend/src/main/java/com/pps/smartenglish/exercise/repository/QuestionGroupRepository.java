package com.pps.smartenglish.exercise.repository;

import com.pps.smartenglish.exercise.domain.QuestionGroup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionGroupRepository extends JpaRepository<QuestionGroup, Long> {

    List<QuestionGroup> findByBankIdOrderBySortOrderAscIdAsc(Long bankId);
}

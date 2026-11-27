package com.pps.smartenglish.exercise.repository;

import com.pps.smartenglish.exercise.domain.ExamBandRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExamBandRuleRepository extends JpaRepository<ExamBandRule, Long> {

    List<ExamBandRule> findByBankIdOrderBySkillAscMinPercentAsc(Long bankId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ExamBandRule r where r.bankId = :bankId")
    void deleteByBank(@Param("bankId") Long bankId);
}

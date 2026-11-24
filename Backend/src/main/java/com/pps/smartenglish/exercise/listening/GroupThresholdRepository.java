package com.pps.smartenglish.exercise.listening;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupThresholdRepository extends JpaRepository<GroupThreshold, Long> {

    List<GroupThreshold> findByRuleId(Long ruleId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from GroupThreshold t where t.ruleId = :ruleId")
    void deleteByRuleId(@Param("ruleId") Long ruleId);
}

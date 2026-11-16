package com.pps.smartenglish.placement.repository;

import com.pps.smartenglish.placement.domain.PlacementBandRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlacementBandRuleRepository extends JpaRepository<PlacementBandRule, Long> {

    List<PlacementBandRule> findAllByOrderBySkillAscMinPercentAsc();

    boolean existsBySkill(String skill);
}

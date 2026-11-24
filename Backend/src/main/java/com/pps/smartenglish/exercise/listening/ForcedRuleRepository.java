package com.pps.smartenglish.exercise.listening;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ForcedRuleRepository extends JpaRepository<ForcedRule, Long> {

    Optional<ForcedRule> findByScopeKey(String scopeKey);
}

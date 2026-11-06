package com.pps.smartenglish.ai.repository;

import com.pps.smartenglish.ai.domain.TokenUsage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenUsageRepository extends JpaRepository<TokenUsage, Long> {}

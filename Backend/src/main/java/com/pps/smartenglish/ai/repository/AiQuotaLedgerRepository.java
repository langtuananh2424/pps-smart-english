package com.pps.smartenglish.ai.repository;

import com.pps.smartenglish.ai.domain.AiQuotaLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiQuotaLedgerRepository extends JpaRepository<AiQuotaLedgerEntry, Long> {}

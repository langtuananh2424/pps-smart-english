package com.pps.smartenglish.exercise.repository;

import com.pps.smartenglish.exercise.domain.BankFaq;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankFaqRepository extends JpaRepository<BankFaq, Long> {

    List<BankFaq> findByBankIdOrderBySortOrderAscIdAsc(Long bankId);

    long countByBankId(Long bankId);
}

package com.pps.smartenglish.exercise.repository;

import com.pps.smartenglish.exercise.domain.GrammarElement;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GrammarElementRepository extends JpaRepository<GrammarElement, Long> {

    List<GrammarElement> findAllByOrderBySortOrderAscIdAsc();

    List<GrammarElement> findByCodeIn(Collection<String> codes);
}

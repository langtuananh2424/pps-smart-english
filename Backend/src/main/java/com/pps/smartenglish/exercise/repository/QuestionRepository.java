package com.pps.smartenglish.exercise.repository;

import com.pps.smartenglish.exercise.domain.Question;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByBankIdOrderBySortOrderAscIdAsc(Long bankId);

    boolean existsByBankId(Long bankId);

    long countByBankId(Long bankId);

    /** [bankId, số câu] cho nhiều bộ trong một truy vấn (danh sách CMS). */
    @Query("select q.bankId, count(q) from Question q where q.bankId in :bankIds group by q.bankId")
    List<Object[]> countByBankIds(@Param("bankIds") Collection<Long> bankIds);
}

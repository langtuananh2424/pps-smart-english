package com.pps.smartenglish.lessoncontent;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface DictionaryRepository extends JpaRepository<DictionaryEntry, Long> {

    List<DictionaryEntry> findByWordKeyOrderByIdAsc(String wordKey, Pageable page);

    /** Tiền tố, trừ khớp chính xác (đã lấy riêng). prefix đã thoát % _ bằng '!'. */
    @Query("select d from DictionaryEntry d where d.wordKey like concat(:prefix, '%') escape '!' and d.wordKey <> :exact "
            + "order by length(d.wordKey), d.wordKey, d.id")
    List<DictionaryEntry> findByPrefix(String prefix, String exact, Pageable page);
}

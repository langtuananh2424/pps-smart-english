package com.pps.smartenglish.lessoncontent;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface GlossaryRepository extends JpaRepository<GlossaryEntry, Long> {

    List<GlossaryEntry> findByLessonIdOrderByIdAsc(Long lessonId);

    long countByLessonId(Long lessonId);

    boolean existsByLessonIdAndPhraseKey(Long lessonId, String phraseKey);

    boolean existsByLessonIdAndPhraseKeyAndIdNot(Long lessonId, String phraseKey, Long id);
}

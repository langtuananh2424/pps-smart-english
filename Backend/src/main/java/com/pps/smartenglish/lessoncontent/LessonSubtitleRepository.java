package com.pps.smartenglish.lessoncontent;

import com.pps.smartenglish.lessoncontent.LessonContentDtos.SubtitleInfo;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface LessonSubtitleRepository extends JpaRepository<LessonSubtitle, Long> {

    Optional<LessonSubtitle> findByLessonIdAndLang(Long lessonId, String lang);

    List<LessonSubtitle> findByLessonId(Long lessonId);

    /** Không nạp content (có thể tới 1 MB mỗi dòng). */
    @Query("select new com.pps.smartenglish.lessoncontent.LessonContentDtos$SubtitleInfo(s.lessonId, s.lang, s.cueCount, s.updatedAt) "
            + "from LessonSubtitle s where s.lessonId in :ids order by s.lessonId, s.lang")
    List<SubtitleInfo> summaries(Collection<Long> ids);

    long deleteByLessonIdAndLang(Long lessonId, String lang);
}

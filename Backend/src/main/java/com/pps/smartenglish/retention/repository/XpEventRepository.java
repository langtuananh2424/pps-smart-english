package com.pps.smartenglish.retention.repository;

import com.pps.smartenglish.retention.domain.XpEvent;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface XpEventRepository extends JpaRepository<XpEvent, Long> {

    /** XP đã nhận theo nguồn và tham chiếu (LESSON: lessonId; EXERCISE, EXERCISE_HIGH: bankId). */
    List<XpEvent> findByUserIdAndSourceInAndRefIdIn(Long userId, Collection<String> sources, Collection<Long> refIds);

    /** Trả 1 chỉ khi chèn được dòng mới (lần đầu cho (user, source, ref)); đồng thời thì bên thua ném lỗi trùng và rollback. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            INSERT INTO xp_events (user_id, source, ref_id, xp)
            SELECT :userId, :source, :refId, :xp
            WHERE NOT EXISTS (SELECT 1 FROM xp_events WHERE user_id = :userId AND source = :source AND ref_id = :refId)
            """)
    int insertIfAbsent(@Param("userId") Long userId, @Param("source") String source, @Param("refId") Long refId,
                       @Param("xp") int xp);
}

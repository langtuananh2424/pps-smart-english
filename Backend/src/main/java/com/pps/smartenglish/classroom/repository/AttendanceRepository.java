package com.pps.smartenglish.classroom.repository;

import com.pps.smartenglish.classroom.domain.AttendanceMark;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceRepository extends JpaRepository<AttendanceMark, Long> {

    boolean existsBySessionId(Long sessionId);

    List<AttendanceMark> findBySessionId(Long sessionId);

    List<AttendanceMark> findBySessionIdIn(Collection<Long> sessionIds);

    /** Điểm danh của một người trong các buổi cho trước. */
    @Query("select m from AttendanceMark m where m.userId = :userId and m.sessionId in :sessionIds")
    List<AttendanceMark> ofUser(@Param("userId") Long userId, @Param("sessionIds") Collection<Long> sessionIds);
}

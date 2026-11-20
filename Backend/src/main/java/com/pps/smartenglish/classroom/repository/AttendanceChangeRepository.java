package com.pps.smartenglish.classroom.repository;

import com.pps.smartenglish.classroom.domain.AttendanceChange;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceChangeRepository extends JpaRepository<AttendanceChange, Long> {

    List<AttendanceChange> findBySessionIdOrderByIdAsc(Long sessionId);
}

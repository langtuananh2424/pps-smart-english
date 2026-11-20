package com.pps.smartenglish.classroom.repository;

import com.pps.smartenglish.classroom.domain.ClassSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {

    Optional<ClassSession> findByIdAndClassId(Long id, Long classId);

    /** Buổi bắt đầu trong [from, to). viewerId = 0: nhân sự thấy tất cả; học viên (id > 0) thấy buổi nhóm và buổi kèm của mình. */
    @Query("select s from ClassSession s where s.classId = :classId and s.startsAt >= :from and s.startsAt < :to "
            + "and (:viewerId = 0 or s.kind = 'GROUP' or s.studentId = :viewerId) order by s.startsAt, s.id")
    List<ClassSession> range(@Param("classId") Long classId, @Param("from") Instant from, @Param("to") Instant to,
                             @Param("viewerId") long viewerId);

    /** Buổi còn hiệu lực, chưa nhắc, bắt đầu trong (now, until]. */
    @Query("select s from ClassSession s where s.status = 'SCHEDULED' and s.remindedAt is null "
            + "and s.startsAt > :now and s.startsAt <= :until order by s.startsAt")
    List<ClassSession> dueForReminder(@Param("now") Instant now, @Param("until") Instant until);

    /** Nhận việc nhắc: chỉ một instance thắng (UPDATE có điều kiện). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ClassSession s set s.remindedAt = :now where s.id = :id and s.remindedAt is null and s.status = 'SCHEDULED'")
    int claimReminder(@Param("id") Long id, @Param("now") Instant now);
}

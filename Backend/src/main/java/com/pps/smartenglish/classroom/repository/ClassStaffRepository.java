package com.pps.smartenglish.classroom.repository;

import com.pps.smartenglish.classroom.domain.ClassStaff;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassStaffRepository extends JpaRepository<ClassStaff, Long> {

    List<ClassStaff> findByClassIdOrderByIdDesc(Long classId);

    Optional<ClassStaff> findByIdAndClassId(Long id, Long classId);

    /** Người này đã có phân công chưa thu hồi và chưa hết hạn cùng vai trò trong lớp. */
    @Query("select count(s) > 0 from ClassStaff s where s.classId = :classId and s.userId = :userId and s.role = :role "
            + "and s.revokedAt is null and (s.assignedTo is null or s.assignedTo >= :today)")
    boolean existsLive(@Param("classId") Long classId, @Param("userId") Long userId, @Param("role") String role,
                       @Param("today") LocalDate today);

    /** Người này đang phụ trách lớp vào ngày today (mọi vai trò). */
    @Query("select count(s) > 0 from ClassStaff s where s.classId = :classId and s.userId = :userId and s.revokedAt is null "
            + "and s.assignedFrom <= :today and (s.assignedTo is null or s.assignedTo >= :today)")
    boolean isStaff(@Param("classId") Long classId, @Param("userId") Long userId, @Param("today") LocalDate today);

    /** Thu hồi mọi phân công giáo viên chính chưa thu hồi: người cũ mất quyền ngay (FR-CLS-04). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ClassStaff s set s.revokedAt = :now where s.classId = :classId and s.role = 'TEACHER' and s.revokedAt is null")
    int revokeTeachers(@Param("classId") Long classId, @Param("now") Instant now);
}

package com.pps.smartenglish.classroom.repository;

import com.pps.smartenglish.classroom.domain.TeachingReportExport;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeachingReportExportRepository extends JpaRepository<TeachingReportExport, Long> {

    /** Các lần xuất có khoảng ngày giao với [from, to]: để tìm mốc của từng buổi. */
    @Query("select e from TeachingReportExport e where e.fromDate <= :to and e.toDate >= :from")
    List<TeachingReportExport> overlapping(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Lần xuất gần nhất có bộ lọc chứa trọn bộ lọc đang xem (khoảng ngày bao trùm, lớp và vai trò null = tất cả); null nếu không có. */
    @Query("select max(e.exportedAt) from TeachingReportExport e where e.fromDate <= :from and e.toDate >= :to "
            + "and (e.classId is null or e.classId = :classId) and (e.role is null or e.role = :role)")
    Instant lastCovering(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("role") String role, @Param("classId") Long classId);
}

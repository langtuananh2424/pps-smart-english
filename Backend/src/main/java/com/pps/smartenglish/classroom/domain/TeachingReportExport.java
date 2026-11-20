package com.pps.smartenglish.classroom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/** FR-CLS-07: một lần xuất báo cáo số buổi dạy; lần sau hiện các buổi bị sửa điểm danh kể từ thời điểm này. */
@Entity
@Table(name = "teaching_report_exports")
public class TeachingReportExport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exported_by", nullable = false, updatable = false)
    private Long exportedBy;

    @Column(name = "exported_at", nullable = false, updatable = false)
    private Instant exportedAt;

    @Column(name = "from_date", nullable = false, updatable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false, updatable = false)
    private LocalDate toDate;

    protected TeachingReportExport() {}

    public TeachingReportExport(Long exportedBy, Instant exportedAt, LocalDate fromDate, LocalDate toDate) {
        this.exportedBy = exportedBy;
        this.exportedAt = exportedAt;
        this.fromDate = fromDate;
        this.toDate = toDate;
    }

    public Instant getExportedAt() { return exportedAt; }
}

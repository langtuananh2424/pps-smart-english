package com.pps.smartenglish.classroom.controller;

import com.pps.smartenglish.classroom.dto.ProgressDtos.ReportClass;
import com.pps.smartenglish.classroom.dto.ProgressDtos.TeachingReport;
import com.pps.smartenglish.classroom.dto.ProgressDtos.Thresholds;
import com.pps.smartenglish.classroom.dto.ProgressDtos.UpdateThresholds;
import com.pps.smartenglish.classroom.service.LagThresholdService;
import com.pps.smartenglish.classroom.service.TeachingReportService;
import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.PathId;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ngưỡng tụt lại theo khóa (FR-CLS-05, quyền class.manage qua /api/admin/classes/**) và báo cáo số buổi dạy (FR-CLS-07, quyền
 * report.teaching qua /api/admin/reports/teaching/**; xem SecurityConfig).
 */
@RestController
class AdminProgressController {

    private final LagThresholdService thresholds;
    private final TeachingReportService teaching;

    AdminProgressController(LagThresholdService thresholds, TeachingReportService teaching) {
        this.thresholds = thresholds;
        this.teaching = teaching;
    }

    @GetMapping("/api/admin/classes/courses/{courseId}/lag-thresholds")
    Thresholds thresholds(@PathVariable String courseId) {
        return thresholds.get(PathId.parse(courseId, "course_not_found", "Không tìm thấy khóa học"));
    }

    @PutMapping("/api/admin/classes/courses/{courseId}/lag-thresholds")
    Thresholds updateThresholds(@PathVariable String courseId, @Valid @RequestBody UpdateThresholds req) {
        return thresholds.update(PathId.parse(courseId, "course_not_found", "Không tìm thấy khóa học"), req);
    }

    @GetMapping("/api/admin/reports/teaching")
    TeachingReport report(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          @RequestParam(required = false) String role, @RequestParam(required = false) Long classId) {
        return teaching.report(from, to, role, classId);
    }

    /** Mọi lớp để chọn ở bộ lọc (Kế toán không có quyền class.manage). */
    @GetMapping("/api/admin/reports/teaching/classes")
    List<ReportClass> reportClasses() {
        return teaching.classes();
    }

    /** POST vì có tác dụng phụ: ghi nhật ký xuất để lần sau báo các buổi bị sửa điểm danh. */
    @PostMapping("/api/admin/reports/teaching/export")
    ResponseEntity<byte[]> export(Authentication auth, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                  @RequestParam(required = false) String role, @RequestParam(required = false) Long classId) {
        byte[] body = teaching.export(CurrentUser.id(auth), from, to, role, classId);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("bao-cao-so-buoi-day-" + from + "_" + to + ".csv").build().toString())
                .body(body);
    }
}

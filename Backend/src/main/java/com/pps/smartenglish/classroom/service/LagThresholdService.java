package com.pps.smartenglish.classroom.service;

import com.pps.smartenglish.catalog.repository.CourseRepository;
import com.pps.smartenglish.classroom.domain.LagThresholds;
import com.pps.smartenglish.classroom.dto.ProgressDtos.Thresholds;
import com.pps.smartenglish.classroom.dto.ProgressDtos.UpdateThresholds;
import com.pps.smartenglish.classroom.repository.LagThresholdsRepository;
import com.pps.smartenglish.shared.audit.AuditRecorder;
import com.pps.smartenglish.shared.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-CLS-05, D-124: giáo vụ cài ngưỡng "tụt lại" theo từng khóa; khóa chưa cài thì dùng mặc định. */
@Service
public class LagThresholdService {

    private final LagThresholdsRepository thresholds;
    private final CourseRepository courses;
    private final AuditRecorder audit;

    LagThresholdService(LagThresholdsRepository thresholds, CourseRepository courses, AuditRecorder audit) {
        this.thresholds = thresholds;
        this.courses = courses;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Thresholds get(long courseId) {
        return thresholds.findById(courseId).map(LagThresholdService::view).orElseGet(() -> view(new LagThresholds(courseId)));
    }

    @Transactional
    public Thresholds update(long courseId, UpdateThresholds req) {
        if (!courses.existsById(courseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "course_not_found", "Không tìm thấy khóa học");
        }
        var row = thresholds.findById(courseId).orElseGet(() -> new LagThresholds(courseId));
        var before = view(row);
        row.set(req.overdueAssignments(), req.absences(), req.unitsBehind(), req.minAvgPercent());
        var after = view(thresholds.saveAndFlush(row));
        audit.record("LAG_THRESHOLDS_UPDATE", "COURSE", courseId, before, after);
        return after;
    }

    private static Thresholds view(LagThresholds t) {
        return new Thresholds(t.getOverdueAssignments(), t.getAbsences(), t.getUnitsBehind(), t.getMinAvgPercent());
    }
}

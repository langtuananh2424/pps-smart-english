package com.pps.smartenglish.classroom.service;

import com.pps.smartenglish.classroom.domain.Assignment;
import com.pps.smartenglish.classroom.domain.ClassMember;
import com.pps.smartenglish.classroom.domain.ClassRoom;
import com.pps.smartenglish.classroom.dto.AssignmentDtos.ProgressRow;
import com.pps.smartenglish.classroom.dto.ProgressDtos.AssignmentCounts;
import com.pps.smartenglish.classroom.dto.ProgressDtos.Attendance;
import com.pps.smartenglish.classroom.dto.ProgressDtos.ChapterTestScore;
import com.pps.smartenglish.classroom.dto.ProgressDtos.ProgressBoard;
import com.pps.smartenglish.classroom.dto.ProgressDtos.StudentProgress;
import com.pps.smartenglish.classroom.dto.ProgressDtos.Thresholds;
import com.pps.smartenglish.classroom.repository.AssignmentRepository;
import com.pps.smartenglish.classroom.repository.ClassMemberRepository;
import com.pps.smartenglish.classroom.repository.ClassReportRepository;
import com.pps.smartenglish.classroom.repository.ClassRepository;
import com.pps.smartenglish.lms.service.UnitUnlockService;
import com.pps.smartenglish.lms.service.UnitUnlockService.Position;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-CLS-05: bảng tiến độ từng học viên của lớp và đánh dấu "tụt lại" theo ngưỡng giáo vụ cài cho khóa (D-124). Mỗi chỉ số được
 * gom bằng truy vấn theo cả lớp (không truy vấn riêng từng học viên) trừ vị trí unit.
 */
@Service
public class ClassProgressService {

    static final String OVERDUE_ASSIGNMENT = "OVERDUE_ASSIGNMENT";
    static final String ABSENCES = "ABSENCES";
    static final String BEHIND_UNIT = "BEHIND_UNIT";
    static final String LOW_SCORE = "LOW_SCORE";
    /** Số bài giao gần nhất đưa vào bảng; đủ cho một khóa, giữ số truy vấn có hạn. */
    static final int ASSIGNMENT_WINDOW = 50;

    private final ClassAccess access;
    private final ClassMemberRepository members;
    private final ClassRepository classRepo;
    private final ClassReportRepository reports;
    private final AssignmentRepository assignments;
    private final AssignmentProgressService assignmentProgress;
    private final UnitUnlockService units;
    private final LagThresholdService thresholds;

    ClassProgressService(ClassAccess access, ClassMemberRepository members, ClassRepository classRepo, ClassReportRepository reports,
                         AssignmentRepository assignments, AssignmentProgressService assignmentProgress, UnitUnlockService units,
                         LagThresholdService thresholds) {
        this.access = access;
        this.members = members;
        this.classRepo = classRepo;
        this.reports = reports;
        this.assignments = assignments;
        this.assignmentProgress = assignmentProgress;
        this.units = units;
        this.thresholds = thresholds;
    }

    @Transactional(readOnly = true)
    public ProgressBoard board(long callerId, long classId) {
        ClassRoom room = access.requireStaff(callerId, classId);
        long courseId = room.getCourseId();
        Thresholds th = thresholds.get(courseId);
        List<Long> ids = members.findByClassIdAndStatus(classId, ClassMember.ACTIVE).stream().map(ClassMember::getUserId).toList();
        if (ids.isEmpty()) {
            return new ProgressBoard(classId, courseId, th, 0, List.of());
        }
        Map<Long, String> names = new HashMap<>();
        classRepo.people(ids).forEach(r -> names.put(((Number) r[0]).longValue(), (String) r[1]));

        long lessonTotal = reports.lessonTotal(courseId);
        Map<Long, Long> lessonsDone = new HashMap<>();
        reports.lessonsDone(courseId, ids).forEach(r -> lessonsDone.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue()));

        Map<Long, Position> positions = new HashMap<>();
        ids.forEach(id -> positions.put(id, units.position(id, courseId)));
        int median = median(positions.values().stream().map(Position::index).toList());
        Map<Long, String> topicTitles = new HashMap<>();
        var topicIds = positions.values().stream().map(Position::topicId).filter(t -> t != null).distinct().toList();
        if (!topicIds.isEmpty()) {
            reports.topicTitles(topicIds).forEach(r -> topicTitles.put(((Number) r[0]).longValue(), (String) r[1]));
        }

        Map<Long, Agg> assignmentAgg = new HashMap<>();
        for (Assignment a : assignments.findByClassIdOrderByIdDesc(classId, PageRequest.of(0, ASSIGNMENT_WINDOW))) {
            if (a.isActive()) {
                assignmentProgress.rows(a).forEach(row -> assignmentAgg.computeIfAbsent(row.userId(), k -> new Agg()).add(row));
            }
        }

        Map<Long, List<ChapterTestScore>> chapter = new HashMap<>();
        reports.chapterTests(courseId, ids).forEach(r -> chapter.computeIfAbsent(((Number) r[0]).longValue(), k -> new ArrayList<>())
                .add(chapterScore(((Number) r[1]).longValue(), (BigDecimal) r[2], r[3] == null ? null : ((Number) r[3]).intValue())));

        Map<Long, Map<String, Integer>> attendance = new HashMap<>();
        reports.attendance(classId, ids).forEach(r -> attendance.computeIfAbsent(((Number) r[0]).longValue(), k -> new HashMap<>())
                .put((String) r[1], ((Number) r[2]).intValue()));

        List<StudentProgress> out = new ArrayList<>();
        for (Long id : ids) {
            Position pos = positions.get(id);
            Agg agg = assignmentAgg.getOrDefault(id, new Agg());
            Attendance att = attendance(attendance.getOrDefault(id, Map.of()));
            Integer avg = agg.avgPercent();
            List<String> reasons = new ArrayList<>();
            if (agg.overdue >= th.overdueAssignments()) reasons.add(OVERDUE_ASSIGNMENT);
            if (att.absent() >= th.absences()) reasons.add(ABSENCES);
            if (median - pos.index() >= th.unitsBehind()) reasons.add(BEHIND_UNIT);
            if (avg != null && avg < th.minAvgPercent()) reasons.add(LOW_SCORE);
            long done = lessonsDone.getOrDefault(id, 0L);
            out.add(new StudentProgress(id, names.get(id), lessonTotal == 0 ? 0 : (int) Math.min(100, done * 100 / lessonTotal),
                    pos.index() + 1, pos.total(), pos.topicId(), topicTitles.get(pos.topicId()),
                    new AssignmentCounts(agg.submitted, agg.late, agg.overdue, agg.pending), avg,
                    chapter.getOrDefault(id, List.of()), att, !reasons.isEmpty(), reasons));
        }
        return new ProgressBoard(classId, courseId, th, median + 1, out);
    }

    /** Trung vị thấp của các vị trí unit (0-based): mốc "chậm hơn lớp". */
    private static int median(List<Integer> indices) {
        var sorted = new ArrayList<>(indices);
        Collections.sort(sorted);
        return sorted.get((sorted.size() - 1) / 2);
    }

    private static ChapterTestScore chapterScore(long topicId, BigDecimal score, Integer max) {
        Integer percent = score == null || max == null || max == 0 ? null
                : score.multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(max), 0, RoundingMode.HALF_UP).intValue();
        return new ChapterTestScore(topicId, score, max, percent);
    }

    private static Attendance attendance(Map<String, Integer> counts) {
        int present = counts.getOrDefault("PRESENT", 0);
        int late = counts.getOrDefault("LATE", 0);
        int absent = counts.getOrDefault("ABSENT", 0);
        int excused = counts.getOrDefault("EXCUSED", 0);
        int denominator = present + late + absent;
        return new Attendance(present, late, absent, excused, denominator == 0 ? null : (present + late) * 100 / denominator);
    }

    /** Gom tiến độ bài giao của một học viên. */
    private static final class Agg {
        int submitted;
        int late;
        int overdue;
        int pending;
        long percentSum;
        int scored;

        void add(ProgressRow r) {
            switch (r.status()) {
                case AssignmentProgressService.SUBMITTED -> submitted++;
                case AssignmentProgressService.LATE -> late++;
                case AssignmentProgressService.OVERDUE -> overdue++;
                default -> pending++;
            }
            if (r.attemptId() != null && r.finalScore() != null && r.maxScore() != null && r.maxScore() > 0) {
                percentSum += r.finalScore().multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(r.maxScore()), 0, RoundingMode.HALF_UP).longValue();
                scored++;
            }
        }

        Integer avgPercent() {
            return scored == 0 ? null : (int) Math.round((double) percentSum / scored);
        }
    }
}

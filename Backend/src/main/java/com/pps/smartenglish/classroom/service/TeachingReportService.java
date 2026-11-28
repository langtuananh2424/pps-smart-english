package com.pps.smartenglish.classroom.service;

import com.pps.smartenglish.classroom.domain.ClassSession;
import com.pps.smartenglish.classroom.domain.TeachingReportExport;
import com.pps.smartenglish.classroom.dto.ProgressDtos.EditedSession;
import com.pps.smartenglish.classroom.dto.ProgressDtos.ReportClass;
import com.pps.smartenglish.classroom.repository.ClassRepository;
import com.pps.smartenglish.classroom.dto.ProgressDtos.TeacherRow;
import com.pps.smartenglish.classroom.dto.ProgressDtos.TeachingReport;
import com.pps.smartenglish.classroom.repository.ClassReportRepository;
import com.pps.smartenglish.classroom.repository.TeachingReportExportRepository;
import com.pps.smartenglish.shared.audit.AuditRecorder;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.persistence.SqlTimes;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-CLS-07, D-111: số buổi và giờ dạy của từng giáo viên, trợ giảng để Kế toán, giáo vụ đối chiếu. Chỉ đếm buổi đã diễn ra và đã
 * điểm danh, tách buổi nhóm và kèm 1-1. Giờ là giờ theo lịch của buổi. Hệ thống không tính lương. Mỗi lần xuất ghi nhật ký; lần sau
 * báo các buổi bị sửa điểm danh kể từ lần xuất trước.
 */
@Service
public class TeachingReportService {

    public static final int MAX_RANGE_DAYS = 366;
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}; // Excel mở CSV UTF-8 đúng tiếng Việt

    private final ClassReportRepository reports;
    private final TeachingReportExportRepository exports;
    private static final Map<String, String> ROLES = Map.of("TEACHER", "Giáo viên", "ASSISTANT", "Trợ giảng");

    private final ClassService classes;
    private final ClassRepository classRepo;
    private final AuditRecorder audit;

    TeachingReportService(ClassReportRepository reports, TeachingReportExportRepository exports, ClassService classes, ClassRepository classRepo,
                          AuditRecorder audit) {
        this.classRepo = classRepo;
        this.reports = reports;
        this.exports = exports;
        this.classes = classes;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ReportClass> classes() {
        return reports.reportClasses().stream().map(r -> new ReportClass(((Number) r[0]).longValue(), (String) r[1], (String) r[2], (String) r[3])).toList();
    }

    @Transactional(readOnly = true)
    public TeachingReport report(LocalDate from, LocalDate to, String role, Long classId) {
        if (from == null || to == null) {
            throw ApiException.validation("from", "Cần khoảng ngày from và to");
        }
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) { // tính cả hai đầu
            throw ApiException.validation("to", "Khoảng ngày phải từ ngày bắt đầu và tối đa " + MAX_RANGE_DAYS + " ngày");
        }
        if (role != null && !ROLES.containsKey(role)) {
            throw ApiException.validation("role", "Vai trò chỉ nhận TEACHER hoặc ASSISTANT");
        }
        if (classId != null && !classRepo.existsById(classId)) {
            throw ApiException.validation("classId", "Không tìm thấy lớp");
        }
        var rows = reports.markedSessions(from.atStartOfDay(ClassService.VN).toInstant(), to.plusDays(1).atStartOfDay(ClassService.VN).toInstant(), role, classId, ClassService.VN.getId());
        Map<String, Acc> byTeacher = new LinkedHashMap<>();
        for (Object[] r : rows) {
            long teacher = ((Number) r[4]).longValue();
            long minutes = Duration.between(SqlTimes.instant(r[6]), SqlTimes.instant(r[7])).toMinutes();
            String teacherRole = r[8] == null ? "TEACHER" : (String) r[8]; // shortcut: người dạy không còn bản ghi phân công (không xảy ra với dữ liệu tạo qua service) tính là giáo viên
            Acc acc = byTeacher.computeIfAbsent(teacher + ":" + teacherRole, k -> new Acc(teacher, (String) r[5], teacherRole));
            if (ClassSession.TUTORING.equals(r[3])) {
                acc.tutoring++;
                acc.tutoringMinutes += minutes;
            } else {
                acc.group++;
                acc.groupMinutes += minutes;
            }
        }
        Instant last = exports.lastCovering(from, to, role, classId);
        List<EditedSession> edited = editedSessions(from, to, rows);
        edited.sort((a, b) -> a.startsAt().compareTo(b.startsAt()));
        List<TeacherRow> teachers = byTeacher.values().stream().map(Acc::row).toList();
        return new TeachingReport(from, to, last, teachers, edited);
    }

    /** Xuất CSV (UTF-8 có BOM) và ghi nhật ký xuất; nội dung hiện các buổi bị sửa kể từ lần xuất trước. */
    @Transactional
    public byte[] export(long userId, LocalDate from, LocalDate to, String role, Long classId) {
        TeachingReport r = report(from, to, role, classId);
        exports.saveAndFlush(new TeachingReportExport(userId, classes.now(), from, to, role, classId));
        audit.record("TEACHING_REPORT_EXPORT", "REPORT", 0, null, Map.of("from", from.toString(), "to", to.toString(), "role", String.valueOf(role), "classId", String.valueOf(classId)));
        StringBuilder sb = new StringBuilder();
        String className = classId == null ? "Tất cả" : classRepo.findById(classId).map(c -> c.getName()).orElse("");
        sb.append(csv("Báo cáo số buổi dạy", from.toString(), to.toString(), "Vai trò: " + (role == null ? "Tất cả" : ROLES.get(role)), "Lớp: " + className));
        sb.append(csv("Mã người dạy", "Tên", "Vai trò", "Số buổi nhóm", "Giờ nhóm", "Số buổi kèm 1-1", "Giờ kèm 1-1", "Tổng số buổi", "Tổng giờ"));
        for (TeacherRow t : r.teachers()) {
            sb.append(csv(String.valueOf(t.userId()), t.displayName(), ROLES.get(t.role()), String.valueOf(t.groupSessions()), t.groupHours().toPlainString(),
                    String.valueOf(t.tutoringSessions()), t.tutoringHours().toPlainString(), String.valueOf(t.totalSessions()),
                    t.totalHours().toPlainString()));
        }
        sb.append('\n').append("Buổi bị sửa điểm danh kể từ lần xuất trước,").append(r.lastExportAt() == null ? "chưa từng xuất" : r.lastExportAt().toString()).append('\n');
        sb.append(csv("Mã buổi", "Lớp", "Mã người dạy", "Tên người dạy", "Bắt đầu", "Số lần sửa", "Sửa gần nhất"));
        for (EditedSession e : r.editedSessions()) {
            sb.append(csv(String.valueOf(e.sessionId()), e.className(), String.valueOf(e.teacherId()), e.teacherName(), e.startsAt().toString(),
                    String.valueOf(e.edits()), e.lastEditedAt().toString()));
        }
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[BOM.length + body.length];
        System.arraycopy(BOM, 0, out, 0, BOM.length);
        System.arraycopy(body, 0, out, BOM.length, body.length);
        return out;
    }

    /**
     * Buổi bị sửa điểm danh sau mốc của chính buổi đó: lần xuất gần nhất có khoảng ngày chứa ngày buổi (giờ VN), lớp và vai trò khớp
     * (null = tất cả). Buổi chưa nằm trong lần xuất nào thì mốc là đầu thời gian, mọi lần sửa đều hiện.
     * shortcut: lấy mọi lần sửa của buổi trong khoảng rồi gom ở Java (không N+1); chuyển sang gom trong SQL nếu số lần sửa lớn.
     */
    private List<EditedSession> editedSessions(LocalDate from, LocalDate to, List<Object[]> sessions) {
        if (sessions.isEmpty()) {
            return new ArrayList<>();
        }
        var past = exports.overlapping(from, to);
        Map<Long, Object[]> bySession = new HashMap<>();
        Map<Long, Instant> since = new HashMap<>();
        Instant min = null;
        for (Object[] s : sessions) {
            long id = ((Number) s[0]).longValue();
            LocalDate day = SqlTimes.instant(s[6]).atZone(ClassService.VN).toLocalDate();
            String sessionRole = s[8] == null ? "TEACHER" : (String) s[8];
            Instant mark = Instant.EPOCH;
            for (TeachingReportExport e : past) {
                if (!day.isBefore(e.getFromDate()) && !day.isAfter(e.getToDate()) && (e.getClassId() == null || e.getClassId().equals(((Number) s[1]).longValue()))
                        && (e.getRole() == null || e.getRole().equals(sessionRole)) && e.getExportedAt().isAfter(mark)) {
                    mark = e.getExportedAt();
                }
            }
            bySession.put(id, s);
            since.put(id, mark);
            min = min == null || mark.isBefore(min) ? mark : min;
        }
        Map<Long, int[]> counts = new HashMap<>();
        Map<Long, Instant> lastEdit = new HashMap<>();
        for (Object[] e : reports.editsInRange(from.atStartOfDay(ClassService.VN).toInstant(), to.plusDays(1).atStartOfDay(ClassService.VN).toInstant(), min)) {
            long id = ((Number) e[0]).longValue();
            Instant at = SqlTimes.instant(e[1]);
            if (bySession.containsKey(id) && at.isAfter(since.get(id))) {
                counts.computeIfAbsent(id, k -> new int[1])[0]++;
                lastEdit.merge(id, at, (x, y) -> x.isAfter(y) ? x : y);
            }
        }
        List<EditedSession> out = new ArrayList<>();
        counts.forEach((id, n) -> {
            Object[] s = bySession.get(id);
            out.add(new EditedSession(id, ((Number) s[1]).longValue(), (String) s[2], ((Number) s[4]).longValue(), (String) s[5],
                    SqlTimes.instant(s[6]), n[0], lastEdit.get(id)));
        });
        return out;
    }

    /** Một dòng CSV; ô chứa dấu phẩy, nháy kép, xuống dòng được bọc nháy kép. Ô bắt đầu bằng = + - @ được chèn ' đầu để Excel không chạy như công thức. */
    private static String csv(String... cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            String c = cells[i] == null ? "" : cells[i];
            if (!c.isEmpty() && "=+-@".indexOf(c.charAt(0)) >= 0 && !c.matches("-?\\d+(\\.\\d+)?")) {
                c = "'" + c;
            }
            if (c.contains(",") || c.contains("\"") || c.contains("\n")) {
                c = '"' + c.replace("\"", "\"\"") + '"';
            }
            sb.append(c);
        }
        return sb.append('\n').toString();
    }

    private static final class Acc {
        final long userId;
        final String name;
        final String role;
        int group;
        int tutoring;
        long groupMinutes;
        long tutoringMinutes;

        Acc(long userId, String name, String role) {
            this.userId = userId;
            this.name = name;
            this.role = role;
        }

        TeacherRow row() {
            return new TeacherRow(userId, name, role, group, hours(groupMinutes), tutoring, hours(tutoringMinutes), group + tutoring,
                    hours(groupMinutes + tutoringMinutes));
        }

        private static BigDecimal hours(long minutes) {
            return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        }
    }
}

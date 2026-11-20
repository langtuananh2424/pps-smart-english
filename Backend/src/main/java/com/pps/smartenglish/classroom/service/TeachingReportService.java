package com.pps.smartenglish.classroom.service;

import com.pps.smartenglish.classroom.domain.ClassSession;
import com.pps.smartenglish.classroom.domain.TeachingReportExport;
import com.pps.smartenglish.classroom.dto.ProgressDtos.EditedSession;
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
    private final ClassService classes;
    private final AuditRecorder audit;

    TeachingReportService(ClassReportRepository reports, TeachingReportExportRepository exports, ClassService classes, AuditRecorder audit) {
        this.reports = reports;
        this.exports = exports;
        this.classes = classes;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public TeachingReport report(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw ApiException.validation("from", "Cần khoảng ngày from và to");
        }
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw ApiException.validation("to", "Khoảng ngày phải từ ngày bắt đầu và tối đa " + MAX_RANGE_DAYS + " ngày");
        }
        var rows = reports.markedSessions(from.atStartOfDay(ClassService.VN).toInstant(), to.plusDays(1).atStartOfDay(ClassService.VN).toInstant());
        Map<Long, Acc> byTeacher = new LinkedHashMap<>();
        Map<Long, Object[]> bySession = new HashMap<>();
        for (Object[] r : rows) {
            long sessionId = ((Number) r[0]).longValue();
            long teacher = ((Number) r[4]).longValue();
            long minutes = Duration.between(SqlTimes.instant(r[6]), SqlTimes.instant(r[7])).toMinutes();
            Acc acc = byTeacher.computeIfAbsent(teacher, k -> new Acc((String) r[5]));
            if (ClassSession.TUTORING.equals(r[3])) {
                acc.tutoring++;
                acc.tutoringMinutes += minutes;
            } else {
                acc.group++;
                acc.groupMinutes += minutes;
            }
            bySession.put(sessionId, r);
        }
        Instant last = lastExport();
        List<EditedSession> edited = new ArrayList<>();
        for (Object[] e : reports.editsSince(last == null ? Instant.EPOCH : last)) {
            Object[] s = bySession.get(((Number) e[0]).longValue());
            if (s != null) {
                edited.add(new EditedSession(((Number) s[0]).longValue(), ((Number) s[1]).longValue(), (String) s[2], ((Number) s[4]).longValue(),
                        (String) s[5], SqlTimes.instant(s[6]), ((Number) e[1]).intValue(), SqlTimes.instant(e[2])));
            }
        }
        edited.sort((a, b) -> a.startsAt().compareTo(b.startsAt()));
        List<TeacherRow> teachers = byTeacher.entrySet().stream().map(en -> en.getValue().row(en.getKey())).toList();
        return new TeachingReport(from, to, last, teachers, edited);
    }

    /** Xuất CSV (UTF-8 có BOM) và ghi nhật ký xuất; nội dung hiện các buổi bị sửa kể từ lần xuất trước. */
    @Transactional
    public byte[] export(long userId, LocalDate from, LocalDate to) {
        TeachingReport r = report(from, to);
        exports.saveAndFlush(new TeachingReportExport(userId, classes.now(), from, to));
        audit.record("TEACHING_REPORT_EXPORT", "REPORT", 0, null, Map.of("from", from.toString(), "to", to.toString()));
        StringBuilder sb = new StringBuilder();
        sb.append("Báo cáo số buổi dạy,").append(from).append(',').append(to).append('\n');
        sb.append(csv("Mã người dạy", "Tên", "Số buổi nhóm", "Giờ nhóm", "Số buổi kèm 1-1", "Giờ kèm 1-1", "Tổng số buổi", "Tổng giờ"));
        for (TeacherRow t : r.teachers()) {
            sb.append(csv(String.valueOf(t.userId()), t.displayName(), String.valueOf(t.groupSessions()), t.groupHours().toPlainString(),
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

    private Instant lastExport() {
        var rows = reports.lastExportAt();
        return rows.isEmpty() ? null : SqlTimes.instant(rows.get(0));
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
        final String name;
        int group;
        int tutoring;
        long groupMinutes;
        long tutoringMinutes;

        Acc(String name) {
            this.name = name;
        }

        TeacherRow row(long userId) {
            return new TeacherRow(userId, name, group, hours(groupMinutes), tutoring, hours(tutoringMinutes), group + tutoring,
                    hours(groupMinutes + tutoringMinutes));
        }

        private static BigDecimal hours(long minutes) {
            return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        }
    }
}

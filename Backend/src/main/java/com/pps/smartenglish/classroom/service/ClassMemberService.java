package com.pps.smartenglish.classroom.service;

import com.pps.smartenglish.classroom.domain.ClassMember;
import com.pps.smartenglish.classroom.domain.ClassRoom;
import com.pps.smartenglish.classroom.dto.ClassDtos.HistoryRow;
import com.pps.smartenglish.classroom.dto.ClassDtos.MemberView;
import com.pps.smartenglish.classroom.dto.ClassDtos.WaitingRow;
import com.pps.smartenglish.classroom.repository.ClassMemberRepository;
import com.pps.smartenglish.classroom.repository.ClassRepository;
import com.pps.smartenglish.commerce.repository.EnrollmentRepository;
import com.pps.smartenglish.retention.service.NotificationService;
import com.pps.smartenglish.shared.audit.AuditRecorder;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.persistence.CmsWrites;
import com.pps.smartenglish.shared.web.CursorPage;
import com.pps.smartenglish.shared.web.Cursors;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-CLS-02, FR-CLS-03: danh sách chờ xếp lớp, xếp, chuyển, rút học viên. Mọi thao tác khóa dòng lớp (chuyển khóa cả hai lớp theo
 * thứ tự id để không deadlock) nên sĩ số không bị vượt khi nhiều giáo vụ xếp cùng lúc; unique live_key chốt thêm ở DB một lớp
 * đang học mỗi khóa. Rời lớp thì giữ dòng cũ với trạng thái mới (lịch sử).
 */
@Service
public class ClassMemberService {

    private final ClassService classes;
    private final ClassRepository classRepo;
    private final ClassMemberRepository members;
    private final EnrollmentRepository enrollments;
    private final NotificationService notifications;
    private final CmsWrites writes;
    private final AuditRecorder audit;

    ClassMemberService(ClassService classes, ClassRepository classRepo, ClassMemberRepository members,
                       EnrollmentRepository enrollments, NotificationService notifications, CmsWrites writes, AuditRecorder audit) {
        this.classes = classes;
        this.classRepo = classRepo;
        this.members = members;
        this.enrollments = enrollments;
        this.notifications = notifications;
        this.writes = writes;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public CursorPage<WaitingRow> waiting(Long courseId, String limit, String cursor) {
        int size = Cursors.size(limit, ClassService.DEFAULT_LIMIT, ClassService.MAX_LIMIT);
        var rows = members.waiting(courseId == null ? 0 : courseId, classes.now(), Cursors.decode(cursor), size + 1);
        boolean more = rows.size() > size;
        var page = (more ? rows.subList(0, size) : rows).stream().map(ClassMemberService::waitingRow).toList();
        return new CursorPage<>(page, more ? Cursors.encode(page.get(size - 1).enrollmentId()) : null);
    }

    @Transactional
    public MemberView assign(long classId, long userId) {
        ClassRoom room = classes.lock(classId);
        ClassService.requireOpen(room);
        requireStudent(userId);
        requireEligible(userId, room.getCourseId());
        requireRoom(room);
        var member = writes.save(members, new ClassMember(classId, room.getCourseId(), userId, classes.now()),
                "already_in_class", "Học viên đã có lớp đang học trong khóa này");
        notifications.classAssigned(userId, classId, member.getId());
        audit.record("CLASS_MEMBER_ASSIGN", "CLASS", classId, null, Map.of("userId", userId));
        return view(member);
    }

    /** Chuyển sang lớp khác cùng khóa: dòng cũ thành TRANSFERRED (flush trước để nhả live_key), tạo dòng mới ACTIVE. */
    @Transactional
    public MemberView transfer(long fromClassId, long userId, long toClassId) {
        if (fromClassId == toClassId) {
            throw ApiException.validation("toClassId", "Lớp đích phải khác lớp hiện tại");
        }
        ClassRoom first = classes.lock(Math.min(fromClassId, toClassId));
        ClassRoom second = classes.lock(Math.max(fromClassId, toClassId));
        ClassRoom from = first.getId() == fromClassId ? first : second;
        ClassRoom to = first.getId() == toClassId ? first : second;
        ClassMember old = members.findByClassIdAndUserIdAndStatus(fromClassId, userId, ClassMember.ACTIVE)
                .orElseThrow(ClassMemberService::notMember);
        if (!from.getCourseId().equals(to.getCourseId())) {
            throw new ApiException(HttpStatus.CONFLICT, "course_mismatch", "Chỉ chuyển giữa các lớp cùng một khóa");
        }
        ClassService.requireOpen(to);
        requireEligible(userId, to.getCourseId());
        requireRoom(to);
        old.leave(ClassMember.TRANSFERRED, classes.now());
        members.saveAndFlush(old);
        var member = writes.save(members, new ClassMember(toClassId, to.getCourseId(), userId, classes.now()),
                "already_in_class", "Học viên đã có lớp đang học trong khóa này");
        notifications.classTransferred(userId, toClassId, member.getId());
        audit.record("CLASS_MEMBER_TRANSFER", "CLASS", fromClassId, Map.of("classId", fromClassId), Map.of("classId", toClassId, "userId", userId));
        return view(member);
    }

    @Transactional
    public MemberView withdraw(long classId, long userId) {
        classes.lock(classId);
        ClassMember member = members.findByClassIdAndUserIdAndStatus(classId, userId, ClassMember.ACTIVE)
                .orElseThrow(ClassMemberService::notMember);
        member.leave(ClassMember.WITHDRAWN, classes.now());
        members.saveAndFlush(member);
        audit.record("CLASS_MEMBER_WITHDRAW", "CLASS", classId, Map.of("userId", userId), null);
        return view(member);
    }

    /** Roster của một lớp, mọi trạng thái, theo thứ tự vào lớp. */
    @Transactional(readOnly = true)
    public List<MemberView> roster(long classId) {
        var rows = members.findByClassIdOrderByIdAsc(classId);
        var people = people(rows.stream().map(ClassMember::getUserId).distinct().toList());
        return rows.stream().map(m -> memberView(m, people.get(m.getUserId()))).toList();
    }

    /** Lịch sử lớp của một học viên (FR-CLS-03: giữ lịch sử xếp, chuyển). */
    @Transactional(readOnly = true)
    public List<HistoryRow> history(long userId) {
        var rows = members.findByUserIdOrderByIdDesc(userId);
        Map<Long, String> names = new HashMap<>();
        classRepo.findAllById(rows.stream().map(ClassMember::getClassId).distinct().toList()).forEach(c -> names.put(c.getId(), c.getName()));
        return rows.stream().map(m -> new HistoryRow(m.getClassId(), names.get(m.getClassId()), m.getStatus(), m.getJoinedAt(), m.getLeftAt())).toList();
    }

    private void requireStudent(long userId) {
        if (people(List.of(userId)).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "student_not_found", "Không tìm thấy học viên");
        }
    }

    /** Đã mua khóa của lớp và còn hạn; không thì 409 not_enrolled. */
    private void requireEligible(long userId, long courseId) {
        boolean ok = enrollments.findByUserIdAndCourseId(userId, courseId).map(e -> e.isActiveAt(classes.now())).orElse(false);
        if (!ok) {
            throw new ApiException(HttpStatus.CONFLICT, "not_enrolled", "Học viên chưa mua khóa này hoặc khóa đã hết hạn");
        }
    }

    private void requireRoom(ClassRoom room) {
        if (members.countByClassIdAndStatus(room.getId(), ClassMember.ACTIVE) >= room.getCapacity()) {
            throw new ApiException(HttpStatus.CONFLICT, "class_full", "Lớp đã đủ sĩ số");
        }
    }

    private MemberView view(ClassMember m) {
        return memberView(m, people(List.of(m.getUserId())).get(m.getUserId()));
    }

    private static MemberView memberView(ClassMember m, String[] person) {
        return new MemberView(m.getUserId(), person == null ? null : person[0], person == null ? null : person[1], m.getStatus(),
                m.getJoinedAt(), m.getLeftAt());
    }

    /** userId → [tên hiển thị, email]. */
    private Map<Long, String[]> people(List<Long> ids) {
        Map<Long, String[]> map = new HashMap<>();
        classRepo.people(ids).forEach(r -> map.put(((Number) r[0]).longValue(), new String[] {(String) r[1], (String) r[2]}));
        return map;
    }

    private static ApiException notMember() {
        return new ApiException(HttpStatus.NOT_FOUND, "member_not_found", "Học viên không đang học lớp này");
    }

    private static WaitingRow waitingRow(Object[] r) {
        return new WaitingRow(((Number) r[0]).longValue(), ((Number) r[1]).longValue(), (String) r[2], (String) r[3],
                ((Number) r[4]).longValue(), (String) r[5], instant(r[6]));
    }

    /** H2 trả Timestamp hoặc OffsetDateTime tùy cột, PostgreSQL trả Timestamp/OffsetDateTime: chấp nhận cả ba. */
    private static Instant instant(Object o) {
        if (o instanceof Instant i) {
            return i;
        }
        if (o instanceof OffsetDateTime odt) {
            return odt.toInstant();
        }
        return ((Timestamp) o).toInstant();
    }
}

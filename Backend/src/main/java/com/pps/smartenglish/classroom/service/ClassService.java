package com.pps.smartenglish.classroom.service;

import com.pps.smartenglish.catalog.domain.Course;
import com.pps.smartenglish.catalog.repository.CourseRepository;
import com.pps.smartenglish.classroom.domain.ClassMember;
import com.pps.smartenglish.classroom.domain.ClassRoom;
import com.pps.smartenglish.classroom.dto.ClassDtos.ClassView;
import com.pps.smartenglish.classroom.dto.ClassDtos.CreateClass;
import com.pps.smartenglish.classroom.dto.ClassDtos.PatchClass;
import com.pps.smartenglish.classroom.dto.ClassDtos.Person;
import com.pps.smartenglish.classroom.repository.ClassMemberRepository;
import com.pps.smartenglish.classroom.repository.ClassRepository;
import com.pps.smartenglish.shared.audit.AuditRecorder;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.persistence.CmsWrites;
import com.pps.smartenglish.shared.web.CursorPage;
import com.pps.smartenglish.shared.web.Cursors;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-CLS-01: giáo vụ tạo, sửa, đóng lớp của khóa có lớp. Không xóa lớp (đã có học viên thì càng không): chỉ đóng.
 * Sửa lớp khóa dòng lớp để sĩ số, đóng lớp không đua với việc xếp học viên.
 */
@Service
public class ClassService {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ClassRepository classes;
    private final CourseRepository courses;
    private final ClassMemberRepository members;
    private final CmsWrites writes;
    private final AuditRecorder audit;
    private final Clock clock;

    ClassService(ClassRepository classes, CourseRepository courses, ClassMemberRepository members, CmsWrites writes,
                 AuditRecorder audit, Clock clock) {
        this.classes = classes;
        this.courses = courses;
        this.members = members;
        this.writes = writes;
        this.audit = audit;
        this.clock = clock;
    }

    /** Ngày hiện tại theo giờ Việt Nam, dùng cho mọi phép "đang phụ trách". */
    public LocalDate today() {
        return LocalDate.now(clock.withZone(VN));
    }

    public Instant now() {
        return clock.instant();
    }

    @Transactional
    public ClassView create(CreateClass req) {
        Course course = courses.findById(req.courseId())
                .orElseThrow(() -> ApiException.validation("courseId", "Không tìm thấy khóa học"));
        if (!Course.CLASS_BASED.equals(course.getDeliveryMode())) {
            throw ApiException.validation("courseId", "Khóa này không phải khóa có lớp");
        }
        requireDates(req.startsOn(), req.endsOn());
        var room = new ClassRoom(course.getId(), req.name().strip(), req.startsOn(), req.endsOn(), req.mode(), req.capacity());
        ClassView after = view(writes.save(classes, room, "class_conflict", "Không lưu được lớp"));
        audit.record("CLASS_CREATE", "CLASS", after.id(), null, after);
        return after;
    }

    @Transactional
    public ClassView update(long id, PatchClass req) {
        ClassRoom room = lock(id);
        requireOpen(room);
        ClassView before = view(room);
        if (req.name() != null) {
            if (req.name().isBlank()) {
                throw ApiException.validation("name", "Không được để trống");
            }
            room.setName(req.name().strip());
        }
        LocalDate from = req.startsOn() != null ? req.startsOn() : room.getStartsOn();
        LocalDate to = req.endsOn() != null ? req.endsOn() : room.getEndsOn();
        requireDates(from, to);
        room.setDates(from, to);
        if (req.mode() != null) {
            room.setMode(req.mode());
        }
        if (req.capacity() != null) {
            if (req.capacity() < members.countByClassIdAndStatus(id, ClassMember.ACTIVE)) {
                throw ApiException.validation("capacity", "Sĩ số nhỏ hơn số học viên đang học");
            }
            room.setCapacity(req.capacity());
        }
        ClassView after = view(writes.save(classes, room, "class_conflict", "Không lưu được lớp"));
        audit.record("CLASS_UPDATE", "CLASS", id, before, after);
        return after;
    }

    /** Đóng lớp (idempotent): học viên đang học chuyển sang COMPLETED nên không quay lại danh sách chờ xếp lớp. */
    @Transactional
    public ClassView close(long id) {
        ClassRoom room = lock(id);
        ClassView before = view(room);
        if (room.isOpen()) {
            room.close();
            Instant now = now();
            members.findByClassIdAndStatus(id, ClassMember.ACTIVE).forEach(m -> m.leave(ClassMember.COMPLETED, now));
            classes.saveAndFlush(room);
            members.flush();
            audit.record("CLASS_CLOSE", "CLASS", id, before, view(room));
        }
        return view(room);
    }

    /** courseId 0 hoặc rỗng và status rỗng là không lọc. */
    @Transactional(readOnly = true)
    public CursorPage<ClassView> list(Long courseId, String status, String limit, String cursor) {
        String st = status == null ? "" : status.strip().toUpperCase();
        if (!st.isEmpty() && !List.of(ClassRoom.OPEN, ClassRoom.CLOSED).contains(st)) {
            throw ApiException.validation("status", "Chỉ nhận OPEN hoặc CLOSED");
        }
        int size = Cursors.size(limit, DEFAULT_LIMIT, MAX_LIMIT);
        List<ClassRoom> rows = classes.page(courseId == null ? 0 : courseId, st, Cursors.decode(cursor), size + 1);
        boolean more = rows.size() > size;
        List<ClassRoom> page = more ? rows.subList(0, size) : rows;
        return new CursorPage<>(views(page), more ? Cursors.encode(page.get(size - 1).getId()) : null);
    }

    public ClassRoom require(long id) {
        return classes.findById(id).orElseThrow(ClassService::notFound);
    }

    public ClassView view(ClassRoom room) {
        return views(List.of(room)).get(0);
    }

    /** Dựng nhiều lớp bằng ba truy vấn gộp (số học viên, giáo viên, tên khóa), không N+1. */
    public List<ClassView> views(List<ClassRoom> rooms) {
        if (rooms.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rooms.stream().map(ClassRoom::getId).toList();
        Map<Long, Long> counts = new HashMap<>();
        classes.activeCounts(ids).forEach(r -> counts.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue()));
        Map<Long, Person> teachers = new HashMap<>();
        classes.teachers(ids, today()).forEach(r -> teachers.put(((Number) r[0]).longValue(),
                new Person(((Number) r[1]).longValue(), (String) r[2])));
        Map<Long, String> titles = new HashMap<>();
        Collection<Long> courseIds = rooms.stream().map(ClassRoom::getCourseId).distinct().toList();
        classes.courseTitles(courseIds).forEach(r -> titles.put(((Number) r[0]).longValue(), (String) r[1]));
        return rooms.stream().map(c -> new ClassView(c.getId(), c.getCourseId(), titles.get(c.getCourseId()), c.getName(),
                c.getStartsOn(), c.getEndsOn(), c.getMode(), c.getCapacity(), c.getStatus(),
                counts.getOrDefault(c.getId(), 0L), teachers.get(c.getId()))).toList();
    }

    /** Khóa dòng lớp; các service xếp, chuyển, phân công dùng chung. */
    ClassRoom lock(long id) {
        return classes.lockById(id).orElseThrow(ClassService::notFound);
    }

    static void requireOpen(ClassRoom room) {
        if (!room.isOpen()) {
            throw new ApiException(HttpStatus.CONFLICT, "class_closed", "Lớp đã đóng");
        }
    }

    private static void requireDates(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw ApiException.validation("endsOn", "Ngày kết thúc phải từ ngày bắt đầu trở đi");
        }
    }

    static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "class_not_found", "Không tìm thấy lớp");
    }
}

package com.pps.smartenglish.classroom.domain;

import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** FR-CLS-01: lớp của một khóa có lớp. Mỗi lớp có ngày bắt đầu riêng (D-120). */
@Entity
@Table(name = "classes")
public class ClassRoom extends Auditable {

    public static final String OPEN = "OPEN";
    public static final String CLOSED = "CLOSED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false, updatable = false)
    private Long courseId;

    @Column(nullable = false)
    private String name;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;

    @Column(nullable = false)
    private String mode;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private String status = OPEN;

    protected ClassRoom() {}

    public ClassRoom(Long courseId, String name, LocalDate startsOn, LocalDate endsOn, String mode, int capacity) {
        this.courseId = courseId;
        this.name = name;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.mode = mode;
        this.capacity = capacity;
    }

    public Long getId() { return id; }
    public Long getCourseId() { return courseId; }
    public String getName() { return name; }
    public LocalDate getStartsOn() { return startsOn; }
    public LocalDate getEndsOn() { return endsOn; }
    public String getMode() { return mode; }
    public int getCapacity() { return capacity; }
    public String getStatus() { return status; }
    public boolean isOpen() { return OPEN.equals(status); }

    public void setName(String name) { this.name = name; }
    public void setDates(LocalDate startsOn, LocalDate endsOn) {
        this.startsOn = startsOn;
        this.endsOn = endsOn;
    }
    public void setMode(String mode) { this.mode = mode; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public void close() { this.status = CLOSED; }
}

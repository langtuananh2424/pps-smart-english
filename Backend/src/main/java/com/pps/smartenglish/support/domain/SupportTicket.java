package com.pps.smartenglish.support.domain;

import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** FR-SUP-02. Đổi trạng thái và người nhận bằng UPDATE có điều kiện trong repository, không sửa trực tiếp entity. */
@Entity
@Table(name = "support_tickets")
public class SupportTicket extends Auditable {

    public static final String OPEN = "OPEN";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String CLOSED = "CLOSED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, updatable = false)
    private String subject;

    @Column(nullable = false, updatable = false)
    private String status = OPEN;

    @Column(name = "assigned_to", insertable = false, updatable = false)
    private Long assignedTo;

    @Column(name = "last_message_at", nullable = false)
    private Instant lastMessageAt;

    @Column(name = "closed_at", insertable = false, updatable = false)
    private Instant closedAt;

    protected SupportTicket() {}

    public SupportTicket(Long userId, String subject, Instant now) {
        this.userId = userId;
        this.subject = subject;
        this.lastMessageAt = now;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getSubject() { return subject; }
    public String getStatus() { return status; }
    public Long getAssignedTo() { return assignedTo; }
    public Instant getLastMessageAt() { return lastMessageAt; }
    public Instant getClosedAt() { return closedAt; }
}

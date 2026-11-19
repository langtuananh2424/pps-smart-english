package com.pps.smartenglish.support.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

/** Tin nhắn trong ticket; bất biến sau khi gửi. */
@Entity
@Immutable
@Table(name = "support_messages")
public class SupportMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "from_staff", nullable = false)
    private boolean fromStaff;

    @Column(nullable = false)
    private String body;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SupportMessage() {}

    public SupportMessage(Long ticketId, Long senderId, boolean fromStaff, String body) {
        this.ticketId = ticketId;
        this.senderId = senderId;
        this.fromStaff = fromStaff;
        this.body = body;
    }

    public Long getId() { return id; }
    public Long getTicketId() { return ticketId; }
    public Long getSenderId() { return senderId; }
    public boolean isFromStaff() { return fromStaff; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}

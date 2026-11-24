package com.pps.smartenglish.exercise.listening;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Một lần cấp link nghe audio của một part trong một lượt làm (D-135). playNo tăng từ 1, unique theo (lượt, part, playNo). */
@Entity
@Table(name = "audio_plays")
public class AudioPlay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attempt_id", nullable = false, updatable = false)
    private Long attemptId;

    @Column(name = "group_id", nullable = false, updatable = false)
    private Long groupId;

    @Column(name = "play_no", nullable = false, updatable = false)
    private int playNo;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected AudioPlay() {}

    public AudioPlay(Long attemptId, Long groupId, int playNo, Instant startedAt, Instant expiresAt) {
        this.attemptId = attemptId;
        this.groupId = groupId;
        this.playNo = playNo;
        this.startedAt = startedAt;
        this.expiresAt = expiresAt;
    }

    /** Đã nghe xong: client báo, hoặc link hết hạn (không thể còn đang phát bằng link cũ). */
    public boolean finished(Instant now) {
        return completedAt != null || !now.isBefore(expiresAt);
    }

    public void complete(Instant now) {
        if (completedAt == null) {
            completedAt = now;
        }
    }

    public int getPlayNo() { return playNo; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCompletedAt() { return completedAt; }
}

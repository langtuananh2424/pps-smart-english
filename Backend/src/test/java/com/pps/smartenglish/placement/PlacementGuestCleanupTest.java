package com.pps.smartenglish.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.pps.smartenglish.placement.service.PlacementGuestCleanupJob;
import com.pps.smartenglish.support.ApiTestBase;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** D-50, FR-TEST-01: chỉ lượt của khách chưa nộp quá 3 ngày bị xóa. */
class PlacementGuestCleanupTest extends ApiTestBase {

    @Autowired PlacementGuestCleanupJob job;

    private long bank() {
        jdbc.update("insert into question_banks (title, status, mode, purpose, time_limit_sec) values ('Đầu vào', 'DRAFT', 'EXAM', 'PLACEMENT', 600)");
        return jdbc.queryForObject("select max(id) from question_banks", Long.class);
    }

    /** daysAgo ngày trước; userId null = khách; submitted = đã nộp. */
    private long attempt(long bank, Long userId, int daysAgo, boolean submitted) {
        var started = OffsetDateTime.now().minusDays(daysAgo);
        jdbc.update("insert into placement_attempts (bank_id, user_id, guest_token_hash, started_at, time_limit_sec, deadline_at, submitted_at)"
                        + " values (?, ?, ?, ?, 600, ?, ?)", bank, userId, UUID.randomUUID().toString().replace("-", ""),
                started, started.plusSeconds(600), submitted ? started.plusSeconds(300) : null);
        return jdbc.queryForObject("select max(id) from placement_attempts", Long.class);
    }

    private boolean exists(long id) {
        return count("select count(*) from placement_attempts where id = ?", id) == 1;
    }

    @Test
    void deletesOnlyAbandonedGuestAttemptsOlderThanTtl() throws Exception {
        long bank = bank();
        var user = register(newEmail(), "Password123!");
        long stale = attempt(bank, null, 4, false);
        long fresh = attempt(bank, null, 2, false);
        long submittedGuest = attempt(bank, null, 4, true);
        long ofUser = attempt(bank, user.id(), 4, false);

        job.tick();

        assertEquals(false, exists(stale));
        assertEquals(true, exists(fresh));
        assertEquals(true, exists(submittedGuest));
        assertEquals(true, exists(ofUser));
        assertEquals(0, job.tick()); // chạy lại không còn gì để xóa
    }
}

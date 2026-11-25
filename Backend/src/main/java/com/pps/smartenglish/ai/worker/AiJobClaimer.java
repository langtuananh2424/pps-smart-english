package com.pps.smartenglish.ai.worker;

import com.pps.smartenglish.ai.domain.AiJob;
import com.pps.smartenglish.ai.repository.AiJobRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Lấy job đến hạn bằng SKIP LOCKED rồi đặt RUNNING; nhiều worker/instance không bao giờ lấy trùng một job.
 * Job hết lease mà đã hết lượt thử thì chỉ ghi nhận id trong transaction claim, rồi FAILED ở transaction riêng sau khi claim commit:
 * khóa dòng job rồi mới khóa dòng lượt (bên trong fail) sẽ ngược thứ tự "lượt trước" và deadlock với worker đang fail cùng job.
 */
@Component
public class AiJobClaimer {

    static final Duration LEASE = Duration.ofSeconds(90);

    private record Claimed(List<Long> ids, List<Long> stale) {}

    private final AiJobRepository jobs;
    private final AiJobFinisher finisher;
    private final TransactionTemplate tx;
    private final Clock clock;

    AiJobClaimer(AiJobRepository jobs, AiJobFinisher finisher, TransactionTemplate tx, Clock clock) {
        this.jobs = jobs;
        this.finisher = finisher;
        this.tx = tx;
        this.clock = clock;
    }

    public List<AiJob> claim(int limit) {
        Claimed c = tx.execute(status -> claimInTx(limit));
        for (Long id : c.stale()) {
            // đọc lại: worker khác có thể đã xong job này trong lúc chờ; fail() còn điều kiện RUNNING và attempts nên an toàn
            jobs.findById(id).filter(j -> AiJob.RUNNING.equals(j.getStatus())).ifPresent(j -> finisher.fail(j, false, "Hết số lần thử"));
        }
        return c.ids().isEmpty() ? List.of() : jobs.findAllById(c.ids());
    }

    private Claimed claimInTx(int limit) {
        Instant now = Instant.now(clock);
        List<Long> claimed = new ArrayList<>();
        List<Long> stale = new ArrayList<>();
        for (Long id : jobs.lockDue(now, limit)) {
            var job = jobs.findById(id).orElseThrow();
            if (AiJob.RUNNING.equals(job.getStatus()) && job.getAttempts() >= AiJobFinisher.MAX_ATTEMPTS) {
                stale.add(id);
                continue;
            }
            jobs.markRunning(id, now.plus(LEASE), now);
            claimed.add(id);
        }
        return new Claimed(claimed, stale);
    }
}

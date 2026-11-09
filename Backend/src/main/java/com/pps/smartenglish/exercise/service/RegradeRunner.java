package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.ai.domain.AiJob;
import com.pps.smartenglish.ai.service.AiJobService;
import com.pps.smartenglish.exercise.config.RegradeProperties;
import com.pps.smartenglish.exercise.repository.RegradeItemRepository;
import com.pps.smartenglish.exercise.repository.RegradeRunRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Một vòng của worker chấm lại: (1) run cũ hơn answer_version hiện tại thì SUPERSEDED và hủy item chưa xong; (2) hòa giải item
 * đang chờ AI mà job đã FAILED; (3) xử lý item PENDING, mỗi item một transaction; (4) chốt run đã hết việc. Chạy lại bao nhiêu
 * lần cũng an toàn: mọi bước là UPDATE có điều kiện hoặc khóa SKIP LOCKED, nên nhiều instance cùng chạy không áp hai lần.
 */
@Component
public class RegradeRunner {

    private static final Logger log = LoggerFactory.getLogger(RegradeRunner.class);

    private final RegradeRunRepository runs;
    private final RegradeItemRepository items;
    private final RegradeProcessor processor;
    private final AiJobService aiJobs;
    private final RegradeProperties props;
    private final Clock clock;

    RegradeRunner(RegradeRunRepository runs, RegradeItemRepository items, RegradeProcessor processor, AiJobService aiJobs,
                  RegradeProperties props, Clock clock) {
        this.runs = runs;
        this.items = items;
        this.processor = processor;
        this.aiJobs = aiJobs;
        this.props = props;
        this.clock = clock;
    }

    public void tick() {
        runs.supersedeStale(Instant.now(clock));
        items.cancelOfSuperseded();
        reconcileFailedJobs();
        for (Long id : items.pendingIds(PageRequest.of(0, props.batchSize()))) {
            try {
                processor.process(id);
            } catch (RuntimeException e) {
                log.warn("Chấm lại item {} lỗi, thử lại ở vòng sau: {}", id, e.getClass().getSimpleName());
            }
        }
        runs.finishDrained(Instant.now(clock));
    }

    /** Item AI_WAITING mà job đã FAILED (hết lần thử): item FAILED, điểm giữ nguyên; Admin bấm xác nhận lại để chạy lại. */
    private void reconcileFailedJobs() {
        List<Object[]> waiting = items.waiting(PageRequest.of(0, props.batchSize()));
        if (waiting.isEmpty()) {
            return;
        }
        var statuses = aiJobs.statuses(waiting.stream().filter(r -> r[1] != null).map(r -> ((Number) r[1]).longValue())
                .collect(Collectors.toSet()));
        Instant now = Instant.now(clock);
        for (Object[] row : waiting) {
            if (row[1] != null && AiJob.FAILED.equals(statuses.get(((Number) row[1]).longValue()))) {
                items.markFailed(((Number) row[0]).longValue(), now);
            }
        }
    }
}

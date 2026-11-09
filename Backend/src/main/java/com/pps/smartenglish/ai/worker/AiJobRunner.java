package com.pps.smartenglish.ai.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pps.smartenglish.ai.config.AiProperties;
import com.pps.smartenglish.ai.domain.AiJob;
import com.pps.smartenglish.ai.domain.AiJobKind;
import com.pps.smartenglish.ai.provider.AiCallException;
import com.pps.smartenglish.shared.event.AiGradingFinished;
import jakarta.annotation.PreDestroy;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Lấy job và chạy theo Strategy AiJobHandler. Bulkhead: executor giới hạn `app.ai.concurrency` luồng (mặc định 10),
 * mỗi vòng chỉ lấy đúng số chỗ trống nên sự cố nhà cung cấp không làm hàng đợi đổ ập vào khi họ sống lại.
 * Không giữ transaction trong lúc gọi mạng.
 * shortcut: chưa có ngắt mạch (5 lỗi liên tiếp/30 s); lỗi tạm thời đã giãn cách thử lại nhưng chưa dừng hẳn việc lấy job.
 */
@Component
public class AiJobRunner {

    private static final Logger log = LoggerFactory.getLogger(AiJobRunner.class);
    private static final int INLINE_BATCH = 100;

    private final AiJobClaimer claimer;
    private final AiJobFinisher finisher;
    private final UsageRecorder usage;
    private final ResultNotifier notifier;
    private final ObjectMapper mapper;
    private final ApplicationEventPublisher events;
    private final Map<AiJobKind, AiJobHandler> handlers = new EnumMap<>(AiJobKind.class);
    private final int concurrency;
    private final ExecutorService executor;
    private final AtomicInteger inFlight = new AtomicInteger();

    AiJobRunner(AiJobClaimer claimer, AiJobFinisher finisher, UsageRecorder usage, ResultNotifier notifier, ObjectMapper mapper,
                List<AiJobHandler> all, AiProperties props, ApplicationEventPublisher events) {
        this.events = events;
        this.claimer = claimer;
        this.finisher = finisher;
        this.usage = usage;
        this.notifier = notifier;
        this.mapper = mapper;
        all.forEach(h -> handlers.put(h.kind(), h));
        this.concurrency = props.concurrency();
        this.executor = Executors.newFixedThreadPool(concurrency, Thread.ofPlatform().name("ai-job-", 0).daemon().factory());
    }

    /** Một vòng poll của worker: lấy tối đa số chỗ trống và giao cho executor. */
    public int tick() {
        int free = concurrency - inFlight.get();
        if (free <= 0) {
            return 0;
        }
        List<AiJob> claimed = claimer.claim(free);
        for (AiJob job : claimed) {
            inFlight.incrementAndGet();
            executor.execute(() -> {
                try {
                    process(job);
                } finally {
                    inFlight.decrementAndGet();
                }
            });
        }
        return claimed.size();
    }

    /** Lấy và chạy ngay trên luồng gọi mọi job đang đến hạn (dùng cho test, không qua executor). */
    public int runDueNow() {
        List<AiJob> claimed = claimer.claim(INLINE_BATCH);
        claimed.forEach(this::process);
        return claimed.size();
    }

    void process(AiJob job) {
        try {
            var c = handlers.get(job.getKind()).execute(job);
            usage.record(job, c.usage());
            if (finisher.complete(job, mapper.valueToTree(c.output()), c.effects())) {
                notifier.publish(job.getId());
                // retention nghe (không có transaction nên chạy ngay); lỗi ở đó không được làm hỏng job
                // job chấm lại là việc của Admin: không có sự kiện nào cho người dùng
                if (job.getRegradeItemId() == null) {
                    events.publishEvent(new AiGradingFinished(job.getId(), job.getUserId(), job.getKind().name()));
                }
            }
        } catch (AiCallException e) {
            usage.record(job, e.usage());
            failed(job, e.isRetryable(), e.getMessage());
        } catch (RuntimeException e) {
            // không log nội dung bài viết: chỉ id job và loại lỗi
            log.error("Job {} lỗi nội bộ: {}", job.getId(), e.getClass().getSimpleName());
            failed(job, true, "Lỗi nội bộ khi xử lý job");
        }
    }

    private void failed(AiJob job, boolean retryable, String error) {
        if (AiJob.FAILED.equals(finisher.fail(job, retryable, error))) {
            notifier.publish(job.getId());
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}

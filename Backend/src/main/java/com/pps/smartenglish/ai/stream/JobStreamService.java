package com.pps.smartenglish.ai.stream;

import com.pps.smartenglish.ai.domain.AiJob;
import com.pps.smartenglish.ai.repository.AiJobRepository;
import com.pps.smartenglish.ai.service.AiJobService;
import com.pps.smartenglish.shared.error.ApiException;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Luồng SSE kết quả job (NFR-PERF-03). Mỗi job một luồng trên mỗi instance: luồng mới thay luồng cũ.
 * Tin Redis (ai:result:{id}) chỉ là tín hiệu đánh thức; nguồn sự thật luôn là DB, và mỗi nhịp keep-alive 15 s đọc lại job
 * nên Redis lỗi hay mất tin cũng không làm kẹt.
 * shortcut: chưa stream từng đoạn (sự kiện `delta`), chỉ có progress rồi result/error.
 */
@Service
public class JobStreamService {

    static final long KEEP_ALIVE_SECONDS = 15;
    private static final long TIMEOUT_MS = 5 * 60_000L;
    private static final Logger log = LoggerFactory.getLogger(JobStreamService.class);

    private final AiJobRepository jobs;
    private final Map<Long, Subscription> subscriptions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("ai-sse-keepalive").daemon().factory());

    JobStreamService(AiJobRepository jobs) {
        this.jobs = jobs;
    }

    private static final class Subscription {
        final SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        volatile ScheduledFuture<?> keepAlive;
        boolean closed;

        synchronized void close() {
            closed = true;
            if (keepAlive != null) {
                keepAlive.cancel(false);
            }
            emitter.complete();
        }
    }

    /** Chỉ chủ job mở được (người khác 404). Job đã xong thì gửi kết quả ngay rồi đóng. */
    public SseEmitter open(long userId, long jobId) {
        AiJob job = jobs.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "job_not_found", "Không tìm thấy job"));
        var sub = new Subscription();
        Subscription old = subscriptions.put(jobId, sub);
        if (old != null) {
            old.close();
        }
        sub.emitter.onCompletion(() -> release(jobId, sub));
        sub.emitter.onTimeout(() -> release(jobId, sub));
        sub.emitter.onError(e -> release(jobId, sub));
        sub.keepAlive = scheduler.scheduleWithFixedDelay(() -> check(jobId, sub), KEEP_ALIVE_SECONDS, KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS);
        send(sub, "progress", job);
        if (job.isTerminal()) {
            finish(sub, job);
        } else {
            check(jobId, sub); // job có thể vừa xong giữa lúc đọc và đăng ký
        }
        return sub.emitter;
    }

    /** Tín hiệu từ Redis: job vừa xong, đọc lại DB rồi gửi. */
    public void onNotify(long jobId) {
        Subscription sub = subscriptions.get(jobId);
        if (sub != null) {
            check(jobId, sub);
        }
    }

    private void check(long jobId, Subscription sub) {
        try {
            jobs.findById(jobId).ifPresent(job -> {
                if (job.isTerminal()) {
                    finish(sub, job);
                } else {
                    send(sub, "progress", job);
                }
            });
        } catch (RuntimeException e) {
            log.warn("Không đọc được job {} cho luồng SSE: {}", jobId, e.toString());
        }
    }

    private void finish(Subscription sub, AiJob job) {
        send(sub, AiJob.DONE.equals(job.getStatus()) ? "result" : "error", job);
        sub.close();
    }

    private void send(Subscription sub, String event, AiJob job) {
        synchronized (sub) {
            if (sub.closed) {
                return;
            }
            try {
                sub.emitter.send(SseEmitter.event().name(event).data(AiJobService.toView(job), MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                sub.closed = true;
                sub.emitter.completeWithError(e);
            }
        }
    }

    private void release(long jobId, Subscription sub) {
        subscriptions.remove(jobId, sub);
        if (sub.keepAlive != null) {
            sub.keepAlive.cancel(false);
        }
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}

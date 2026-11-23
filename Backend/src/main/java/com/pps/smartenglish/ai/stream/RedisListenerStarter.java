package com.pps.smartenglish.ai.stream;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.Lifecycle;
import org.springframework.context.SmartLifecycle;

/**
 * Khởi động container nghe Redis ở nền và thử lại đến khi được, để Redis lên chậm không làm backend sập.
 * Container.start() lỗi vẫn giữ cờ started=true nên phải stop() trước khi start() lại.
 */
class RedisListenerStarter implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(RedisListenerStarter.class);

    private final Lifecycle target;
    private final Duration retryInterval;
    private final Supplier<ScheduledExecutorService> schedulerFactory;
    private volatile ScheduledExecutorService scheduler;
    private volatile boolean running;

    RedisListenerStarter(Lifecycle target, Duration retryInterval) {
        this(target, retryInterval, () -> Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "ai-redis-listener-starter");
            t.setDaemon(true);
            return t;
        }));
    }

    RedisListenerStarter(Lifecycle target, Duration retryInterval, Supplier<ScheduledExecutorService> schedulerFactory) {
        this.target = target;
        this.retryInterval = retryInterval;
        this.schedulerFactory = schedulerFactory;
    }

    RedisListenerStarter(Lifecycle target, Duration retryInterval, ScheduledExecutorService scheduler) {
        this(target, retryInterval, () -> scheduler);
    }

    @Override
    public synchronized void start() {
        running = true;
        scheduler = schedulerFactory.get(); // stop() đã shutdown scheduler cũ nên mỗi lần start cần cái mới
        scheduler.execute(this::attempt);
    }

    private void attempt() {
        if (!running) {
            return;
        }
        try {
            target.start();
            if (!running) {
                target.stop(); // stop() đến giữa lúc đang kết nối: không để container chạy sau khi tắt
                return;
            }
            log.info("Đã đăng ký kênh Redis kết quả AI");
        } catch (RuntimeException e) {
            log.warn("Chưa kết nối được Redis cho kênh kết quả AI, thử lại sau {}: {}", retryInterval, e.getMessage());
            try {
                target.stop();
            } catch (RuntimeException ignored) {
                // trạng thái container đã hỏng một nửa, chỉ cần reset cờ started
            }
            ScheduledExecutorService sc = scheduler;
            if (running && sc != null) {
                try {
                    sc.schedule(this::attempt, retryInterval.toMillis(), TimeUnit.MILLISECONDS);
                } catch (RejectedExecutionException stopped) {
                    // stop() vừa tắt scheduler: không thử lại nữa
                }
            }
        }
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        target.stop();
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}

package com.pps.smartenglish.ai.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Vòng lặp của worker (tắt bằng app.ai.worker-enabled=false để tách vai api/worker). */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
class AiJobWorker {

    private static final Logger log = LoggerFactory.getLogger(AiJobWorker.class);

    private final AiJobRunner runner;

    AiJobWorker(AiJobRunner runner) {
        this.runner = runner;
    }

    @Scheduled(fixedDelay = 1000)
    void poll() {
        try {
            runner.tick();
        } catch (RuntimeException e) {
            log.warn("Vòng poll hàng đợi AI lỗi: {}", e.toString());
        }
    }
}

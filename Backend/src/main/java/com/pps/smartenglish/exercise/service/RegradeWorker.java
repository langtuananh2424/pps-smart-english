package com.pps.smartenglish.exercise.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Vòng lặp chấm lại bài cũ (tắt bằng app.regrade.enabled=false, test gọi RegradeRunner.tick() trực tiếp). */
@Component
@ConditionalOnProperty(prefix = "app.regrade", name = "enabled", havingValue = "true", matchIfMissing = true)
class RegradeWorker {

    private static final Logger log = LoggerFactory.getLogger(RegradeWorker.class);

    private final RegradeRunner runner;

    RegradeWorker(RegradeRunner runner) {
        this.runner = runner;
    }

    @Scheduled(fixedDelayString = "${app.regrade.poll-ms:2000}")
    void poll() {
        try {
            runner.tick();
        } catch (RuntimeException e) {
            log.warn("Vòng chấm lại bài cũ lỗi: {}", e.toString());
        }
    }
}

package com.pps.smartenglish.commerce.service;

import com.pps.smartenglish.commerce.repository.OrderRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Đơn PENDING quá hạn thanh toán chuyển EXPIRED; một câu UPDATE nên nhiều instance chạy cùng lúc vẫn an toàn. */
@Component
public class OrderExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(OrderExpiryJob.class);

    private final OrderRepository orders;
    private final Clock clock;

    OrderExpiryJob(OrderRepository orders, Clock clock) {
        this.orders = orders;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1M")
    public void expire() {
        try {
            int n = orders.expirePending(Instant.now(clock));
            if (n > 0) {
                log.info("Đã chuyển {} đơn quá hạn sang EXPIRED", n);
            }
        } catch (RuntimeException e) {
            log.warn("Job hết hạn đơn lỗi: {}", e.getMessage());
        }
    }
}

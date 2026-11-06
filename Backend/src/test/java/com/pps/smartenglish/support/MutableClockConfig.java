package com.pps.smartenglish.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Đồng hồ chỉnh được để test qua nửa đêm giờ Việt Nam. Mặc định chạy theo giờ thật; reset() trả về mặc định. */
@TestConfiguration
public class MutableClockConfig {

    public static class MutableClock extends Clock {

        private final ZoneId zone;
        private final MutableClock root;
        private volatile Instant fixed;

        MutableClock() {
            this(ZoneOffset.UTC, null);
        }

        private MutableClock(ZoneId zone, MutableClock root) {
            this.zone = zone;
            this.root = root;
        }

        public void set(Instant instant) {
            (root == null ? this : root).fixed = instant;
        }

        public void reset() {
            set(null);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(zone, root == null ? this : root);
        }

        @Override
        public Instant instant() {
            Instant f = (root == null ? this : root).fixed;
            return f != null ? f : Instant.now();
        }
    }

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock();
    }
}

package com.pps.smartenglish.shared.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Dự phòng khi Redis lỗi: đếm riêng từng instance, tự dọn mục hết hạn. */
@Component
class MemoryCounterStore implements CounterStore {

    private static final Duration PURGE_EVERY = Duration.ofMinutes(1);

    private record Entry(long count, Instant expiresAt) {}

    private final Clock clock;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private volatile Instant lastPurge;

    MemoryCounterStore(Clock clock) {
        this.clock = clock;
        this.lastPurge = clock.instant();
    }

    @Override
    public long increment(String key, Duration ttl) {
        Instant now = clock.instant();
        purge(now);
        return entries.compute(key, (k, e) -> e == null || !e.expiresAt().isAfter(now)
                ? new Entry(1, now.plus(ttl))
                : new Entry(e.count() + 1, e.expiresAt())).count();
    }

    @Override
    public long secondsLeft(String key) {
        Entry e = entries.get(key);
        return e == null ? 1L : Math.max(1L, Duration.between(clock.instant(), e.expiresAt()).toSeconds() + 1);
    }

    @Override
    public void reset(String key) {
        entries.remove(key);
    }

    private void purge(Instant now) {
        if (Duration.between(lastPurge, now).compareTo(PURGE_EVERY) > 0) {
            lastPurge = now;
            entries.values().removeIf(e -> !e.expiresAt().isAfter(now));
        }
    }
}

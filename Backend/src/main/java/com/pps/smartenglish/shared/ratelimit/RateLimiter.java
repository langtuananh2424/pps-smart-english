package com.pps.smartenglish.shared.ratelimit;

import com.pps.smartenglish.shared.error.ApiException;
import java.time.Duration;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Giới hạn tần suất theo cửa sổ cố định. Redis lỗi thì chuyển sang bộ đếm trong bộ nhớ theo
 * fallbackKey do caller truyền (từng instance), không bỏ trống giới hạn.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final CounterStore primary;
    private final CounterStore fallback;

    RateLimiter(RedisCounterStore primary, MemoryCounterStore fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    /** Tăng bộ đếm; true nếu vẫn trong giới hạn. */
    public boolean hit(String key, int limit, Duration ttl, String fallbackKey) {
        return increment(key, ttl, fallbackKey) <= limit;
    }

    /** Như hit nhưng ném 429 khi vượt. */
    public void requireHit(String key, int limit, Duration ttl, String fallbackKey) {
        if (!hit(key, limit, ttl, fallbackKey)) {
            throw ApiException.rateLimited(run(key, fallbackKey, CounterStore::secondsLeft));
        }
    }

    private long increment(String key, Duration ttl, String fallbackKey) {
        return run(key, fallbackKey, (store, k) -> store.increment(k, ttl));
    }

    public void reset(String key, String fallbackKey) {
        run(key, fallbackKey, (store, k) -> {
            store.reset(k);
            return 0L;
        });
    }

    private long run(String key, String fallbackKey, BiFunction<CounterStore, String, Long> op) {
        try {
            return op.apply(primary, key);
        } catch (RuntimeException e) {
            log.warn("Redis không dùng được, chuyển sang bộ đếm trong bộ nhớ: {}", e.toString());
            return op.apply(fallback, fallbackKey);
        }
    }
}

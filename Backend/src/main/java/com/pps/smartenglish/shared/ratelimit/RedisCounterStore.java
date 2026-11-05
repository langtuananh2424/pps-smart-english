package com.pps.smartenglish.shared.ratelimit;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
class RedisCounterStore implements CounterStore {

    private final StringRedisTemplate redis;

    RedisCounterStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long increment(String key, Duration ttl) {
        // setIfAbsent trước để key luôn có TTL, kể cả khi increment là lệnh tạo key.
        redis.opsForValue().setIfAbsent(key, "0", ttl);
        Long n = redis.opsForValue().increment(key);
        return n == null ? 1L : n;
    }

    @Override
    public long secondsLeft(String key) {
        Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
        return ttl == null || ttl < 1 ? 1L : ttl;
    }

    @Override
    public void reset(String key) {
        redis.delete(key);
    }
}

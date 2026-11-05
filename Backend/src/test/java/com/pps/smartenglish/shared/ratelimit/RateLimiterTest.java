package com.pps.smartenglish.shared.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pps.smartenglish.shared.error.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RateLimiterTest {

    private static final Duration TTL = Duration.ofMinutes(15);

    private static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @SuppressWarnings("unchecked")
    @Test
    void redisPathSetsTtlBeforeIncrementing() {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment("k")).thenReturn(1L);
        var limiter = new RateLimiter(new RedisCounterStore(redis), new MemoryCounterStore(new MutableClock()));

        assertThat(limiter.hit("k", 3, TTL, "fb")).isTrue();

        var order = inOrder(ops);
        order.verify(ops).setIfAbsent("k", "0", TTL);
        order.verify(ops).increment("k");
    }

    @Test
    void fallsBackToMemoryPerFallbackKeyWhenRedisFails() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));
        when(redis.delete(any(String.class))).thenThrow(new RedisConnectionFailureException("down"));
        var limiter = new RateLimiter(new RedisCounterStore(redis), new MemoryCounterStore(new MutableClock()));

        for (int i = 0; i < 5; i++) {
            limiter.requireHit("rl:a", 5, TTL, "ip:1");
        }
        assertThatThrownBy(() -> limiter.requireHit("rl:b", 5, TTL, "ip:1"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(429);
                    assertThat(e.getRetryAfterSeconds()).isBetween(1L, 900L);
                });
        limiter.requireHit("rl:b", 5, TTL, "ip:2"); // IP khác không bị ảnh hưởng

        limiter.reset("rl:a", "ip:1");
        limiter.requireHit("rl:a", 5, TTL, "ip:1");
    }

    @Test
    void memoryCountersExpireAfterTtl() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));
        var clock = new MutableClock();
        var limiter = new RateLimiter(new RedisCounterStore(redis), new MemoryCounterStore(clock));

        assertThat(limiter.hit("k", 1, TTL, "fb")).isTrue();
        assertThat(limiter.hit("k", 1, TTL, "fb")).isFalse();
        clock.now = clock.now.plus(TTL).plusSeconds(1);
        assertThat(limiter.hit("k", 1, TTL, "fb")).isTrue();
    }
}

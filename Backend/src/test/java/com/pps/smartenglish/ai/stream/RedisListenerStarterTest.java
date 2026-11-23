package com.pps.smartenglish.ai.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.context.Lifecycle;

class RedisListenerStarterTest {

    /** Giả container: start() ném lỗi failTimes lần đầu, như Redis chưa lên. */
    static class FakeTarget implements Lifecycle {
        final AtomicInteger starts = new AtomicInteger();
        final AtomicInteger stops = new AtomicInteger();
        final CountDownLatch ok = new CountDownLatch(1);
        final int failTimes;

        FakeTarget(int failTimes) {
            this.failTimes = failTimes;
        }

        @Override
        public void start() {
            if (starts.incrementAndGet() <= failTimes) {
                throw new IllegalStateException("Unable to connect to Redis");
            }
            ok.countDown();
        }

        @Override
        public void stop() {
            stops.incrementAndGet();
        }

        @Override
        public boolean isRunning() {
            return ok.getCount() == 0;
        }
    }

    @Test
    void retriesUntilSuccessThenStopsRetrying() throws Exception {
        var target = new FakeTarget(2);
        var starter = new RedisListenerStarter(target, Duration.ofMillis(10));
        starter.start();
        assertThat(target.ok.await(5, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(100);
        assertThat(target.starts.get()).isEqualTo(3);
        assertThat(target.stops.get()).isEqualTo(2); // reset sau mỗi lần lỗi
        starter.stop();
    }

    @Test
    void stopCancelsFurtherRetries() throws Exception {
        var target = new FakeTarget(Integer.MAX_VALUE);
        var starter = new RedisListenerStarter(target, Duration.ofMillis(10));
        starter.start();
        Thread.sleep(100);
        starter.stop();
        int after = target.starts.get();
        Thread.sleep(100);
        assertThat(after).isGreaterThan(1);
        assertThat(target.starts.get()).isEqualTo(after);
        assertThat(starter.isRunning()).isFalse();
    }
}

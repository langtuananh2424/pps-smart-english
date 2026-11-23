package com.pps.smartenglish.ai.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.context.Lifecycle;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** Issue #57, ca biên: dừng giữa lúc đang kết nối, khởi động lại sau khi dừng, container thật khi Redis chết. */
class RedisListenerStarterEdgeTest {

    /** start() treo đến khi được thả (như kết nối Redis chậm), rồi thành công; không phản ứng với interrupt. */
    static class SlowTarget implements Lifecycle {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch finished = new CountDownLatch(1);
        final AtomicBoolean running = new AtomicBoolean();

        @Override
        public void start() {
            entered.countDown();
            boolean interrupted = false;
            while (true) {
                try {
                    release.await();
                    break;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            running.set(true);
            finished.countDown();
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void stop() {
            running.set(false);
        }

        @Override
        public boolean isRunning() {
            return running.get();
        }
    }

    @Test
    void stopDuringSlowStartDoesNotLeaveListenerRunning() throws Exception {
        var target = new SlowTarget();
        var starter = new RedisListenerStarter(target, Duration.ofMillis(10));
        starter.start();
        assertThat(target.entered.await(5, TimeUnit.SECONDS)).isTrue();

        starter.stop(); // ứng dụng tắt khi container còn đang kết nối
        target.release.countDown(); // kết nối xong sau khi đã tắt
        assertThat(target.finished.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(starter.isRunning()).isFalse();
        // finished đếm ngược trước khi start() trả về nên stop() bù của starter chạy ngay sau đó: chờ ngắn thay vì kiểm tức thì
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (target.isRunning() && System.nanoTime() < until) {
            Thread.sleep(5);
        }
        assertThat(target.isRunning()).as("container không được chạy tiếp sau khi starter đã dừng").isFalse();
    }

    @Test
    void canStartAgainAfterStop() throws Exception {
        var failing = new RedisListenerStarterTest.FakeTarget(Integer.MAX_VALUE);
        var starter = new RedisListenerStarter(failing, Duration.ofMillis(10));
        starter.start();
        starter.stop();
        // Lifecycle cho phép stop() rồi start() lại (ConfigurableApplicationContext.stop()/start())
        assertThatCode(starter::start).doesNotThrowAnyException();
        int before = failing.starts.get();
        Thread.sleep(200);
        assertThat(failing.starts.get()).as("thử lại phải tiếp tục sau khi start lại").isGreaterThan(before);
        starter.stop();
    }

    /** Lifecycle đếm số lần start, ủy quyền cho container thật. */
    static class Counting implements Lifecycle {
        final Lifecycle inner;
        final AtomicInteger starts = new AtomicInteger();

        Counting(Lifecycle inner) {
            this.inner = inner;
        }

        @Override
        public void start() {
            starts.incrementAndGet();
            inner.start();
        }

        @Override
        public void stop() {
            inner.stop();
        }

        @Override
        public boolean isRunning() {
            return inner.isRunning();
        }
    }

    @Test
    void realContainerWithRedisDownRetriesAndStopsPromptly() throws Exception {
        var factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", 1));
        factory.afterPropertiesSet();
        var container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener((m, p) -> {}, new PatternTopic("ai:result:*"));
        container.afterPropertiesSet();
        var counting = new Counting(container);
        var scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        var starter = new RedisListenerStarter(counting, Duration.ofMillis(50), scheduler);
        try {
            starter.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (counting.starts.get() < 2 && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            assertThat(counting.starts.get()).as("container thật phải báo lỗi để starter thử lại").isGreaterThanOrEqualTo(2);

            long t0 = System.nanoTime();
            starter.stop();
            long stopMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            assertThat(stopMs).as("stop() không được treo lúc tắt ứng dụng").isLessThan(3000);

            int after = counting.starts.get();
            Thread.sleep(300);
            assertThat(counting.starts.get()).as("không thử lại sau khi đã dừng").isEqualTo(after);
            assertThat(container.isRunning()).isFalse();
            assertThat(scheduler.awaitTermination(3, TimeUnit.SECONDS)).as("luồng nền phải kết thúc").isTrue();
        } finally {
            container.destroy();
            factory.destroy();
        }
    }
}

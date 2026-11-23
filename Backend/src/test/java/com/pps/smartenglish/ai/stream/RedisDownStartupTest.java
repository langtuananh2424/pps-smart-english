package com.pps.smartenglish.ai.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.pps.smartenglish.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/** Issue #57: Redis chưa sẵn sàng thì backend vẫn phải khởi động xong. */
@TestPropertySource(properties = {
    "app.ai.redis-notify=true",
    "app.ai.redis-retry-interval=50ms",
    "spring.data.redis.host=127.0.0.1",
    "spring.data.redis.port=1"
})
/** DirtiesContext: đóng context sau test để không còn thử Redis mỗi 50ms suốt bộ test. */
@DirtiesContext
class RedisDownStartupTest extends ApiTestBase {

    @Autowired RedisListenerStarter starter;

    @Test
    void contextLoadsWithoutRedis() {
        assertThat(starter.isRunning()).isTrue();
    }
}

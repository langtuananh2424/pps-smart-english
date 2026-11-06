package com.pps.smartenglish.ai.stream;

import com.pps.smartenglish.ai.worker.ResultNotifier;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** Nghe ai:result:* từ worker (có thể ở instance khác) và đánh thức luồng SSE tương ứng. Tắt được khi test. */
@Configuration
@ConditionalOnProperty(prefix = "app.ai", name = "redis-notify", havingValue = "true", matchIfMissing = true)
class ResultListenerConfig {

    private static final Logger log = LoggerFactory.getLogger(ResultListenerConfig.class);

    @Bean
    RedisMessageListenerContainer aiResultListenerContainer(RedisConnectionFactory factory, JobStreamService streams) {
        var container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener((message, pattern) -> {
            String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
            try {
                streams.onNotify(Long.parseLong(channel.substring(ResultNotifier.CHANNEL_PREFIX.length())));
            } catch (RuntimeException e) {
                log.warn("Bỏ qua tin Redis không hợp lệ trên kênh {}", channel);
            }
        }, new PatternTopic(ResultNotifier.CHANNEL_PREFIX + "*"));
        return container;
    }
}

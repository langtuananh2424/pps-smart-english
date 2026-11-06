package com.pps.smartenglish.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pps.smartenglish.admin.dto.DashboardResponse;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Cache 1 phút cho báo cáo (system-design 5.8: "thời gian thực" = truy vấn trực tiếp, cache 1 phút). Redis chỉ là cache,
 * nên lỗi đọc/ghi hay dữ liệu hỏng đều tính như không có cache và báo cáo vẫn được tính thẳng từ CSDL.
 */
@Component
class DashboardCache {

    static final Duration TTL = Duration.ofSeconds(60);
    private static final Logger log = LoggerFactory.getLogger(DashboardCache.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    DashboardCache(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    Optional<DashboardResponse> get(String key) {
        try {
            String json = redis.opsForValue().get(key);
            return json == null ? Optional.empty() : Optional.of(mapper.readValue(json, DashboardResponse.class));
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("Không đọc được cache báo cáo, tính trực tiếp: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    void put(String key, DashboardResponse response) {
        try {
            redis.opsForValue().set(key, mapper.writeValueAsString(response), TTL);
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("Không ghi được cache báo cáo: {}", e.getClass().getSimpleName());
        }
    }
}

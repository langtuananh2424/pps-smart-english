package com.pps.smartenglish.shared.ratelimit;

import java.time.Duration;

/** Nơi lưu bộ đếm (Strategy): Redis dùng chính, bộ nhớ dự phòng. */
interface CounterStore {

    /** Tăng bộ đếm, tạo mới với TTL nếu chưa có; trả giá trị sau khi tăng. */
    long increment(String key, Duration ttl);

    /** Số giây còn lại trước khi bộ đếm hết hạn (tối thiểu 1). */
    long secondsLeft(String key);

    void reset(String key);
}

package com.pps.smartenglish.support;

import com.pps.smartenglish.shared.storage.ObjectStorage;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** ObjectStorage giả: URL mang theo key và số giây hạn để test kiểm chứng. */
@TestConfiguration
public class FakeStorageConfig {

    @Bean
    ObjectStorage objectStorage() {
        return (key, ttl) -> "http://fake-storage/" + key + "?ttl=" + ttl.toSeconds();
    }
}

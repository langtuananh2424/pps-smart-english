package com.pps.smartenglish.support;

import com.pps.smartenglish.shared.storage.ObjectStorage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** ObjectStorage giả trong bộ nhớ: URL mang theo key và số giây hạn để test kiểm chứng. */
@TestConfiguration
public class FakeStorageConfig {

    public static class FakeStorage implements ObjectStorage {

        public record Stored(byte[] data, String contentType) {}

        public record PostRequest(String key, String contentType, long maxBytes, Duration ttl) {}

        public final Map<String, Stored> objects = new ConcurrentHashMap<>();
        public volatile PostRequest lastPost;

        public void put(String key, byte[] data) {
            objects.put(key, new Stored(data, "audio/wav"));
        }

        @Override
        public String presignGet(String key, Duration ttl) {
            return "http://fake-storage/" + key + "?ttl=" + ttl.toSeconds();
        }

        @Override
        public PostForm presignPost(String key, String contentType, long maxBytes, Duration ttl) {
            lastPost = new PostRequest(key, contentType, maxBytes, ttl);
            return new PostForm("http://fake-storage/bucket", Map.of("key", key, "Content-Type", contentType,
                    "policy", "fake-policy", "x-amz-signature", "fake-signature"));
        }

        @Override
        public Optional<ObjectMeta> head(String key) {
            return Optional.ofNullable(objects.get(key)).map(s -> new ObjectMeta(s.data().length, s.contentType()));
        }

        @Override
        public byte[] readHead(String key, int maxBytes) {
            var s = objects.get(key);
            return Arrays.copyOf(s.data(), Math.min(maxBytes, s.data().length));
        }

        @Override
        public InputStream open(String key) {
            return new ByteArrayInputStream(objects.get(key).data());
        }

        @Override
        public void put(String key, byte[] data, String contentType) {
            objects.put(key, new Stored(data, contentType));
        }

        @Override
        public void delete(String key) {
            objects.remove(key);
        }
    }

    @Bean
    FakeStorage objectStorage() {
        return new FakeStorage();
    }
}

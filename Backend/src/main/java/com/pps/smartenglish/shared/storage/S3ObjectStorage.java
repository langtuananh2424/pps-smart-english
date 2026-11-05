package com.pps.smartenglish.shared.storage;

import java.net.URI;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** MinIO qua S3 SDK, path-style. Hai endpoint: nội bộ để quản trị bucket, công khai để ký URL. */
@Component
@ConditionalOnProperty(prefix = "app.storage", name = "enabled", havingValue = "true", matchIfMissing = true)
class S3ObjectStorage implements ObjectStorage, ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(S3ObjectStorage.class);
    private static final int BUCKET_ATTEMPTS = 10;

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;

    S3ObjectStorage(@Value("${app.storage.endpoint}") String endpoint,
                    @Value("${app.storage.public-url}") String publicUrl,
                    @Value("${app.storage.access-key}") String accessKey,
                    @Value("${app.storage.secret-key}") String secretKey,
                    @Value("${app.storage.bucket}") String bucket) {
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        var pathStyle = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        this.bucket = bucket;
        this.client = S3Client.builder().endpointOverride(URI.create(endpoint)).region(Region.US_EAST_1)
                .credentialsProvider(credentials).serviceConfiguration(pathStyle).build();
        this.presigner = S3Presigner.builder().endpointOverride(URI.create(publicUrl)).region(Region.US_EAST_1)
                .credentialsProvider(credentials).serviceConfiguration(pathStyle).build();
    }

    @Override
    public String presignGet(String key, Duration ttl) {
        var get = GetObjectRequest.builder().bucket(bucket).key(key).build();
        return presigner.presignGetObject(r -> r.signatureDuration(ttl).getObjectRequest(get)).url().toString();
    }

    /** Tạo bucket ở luồng nền: MinIO chưa sẵn sàng chỉ ghi WARN, backend vẫn lên (bài YouTube vẫn dùng được). */
    @Override
    public void run(ApplicationArguments args) {
        Thread.ofVirtual().name("minio-bucket-init").start(this::ensureBucket);
    }

    private void ensureBucket() {
        for (int i = 1; i <= BUCKET_ATTEMPTS; i++) {
            try {
                try {
                    client.headBucket(r -> r.bucket(bucket));
                } catch (NoSuchBucketException e) {
                    client.createBucket(r -> r.bucket(bucket));
                    log.info("Đã tạo bucket {}", bucket);
                }
                return;
            } catch (RuntimeException e) {
                log.warn("Chưa chuẩn bị được bucket {} (lần {}/{}): {}", bucket, i, BUCKET_ATTEMPTS, e.getMessage());
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}

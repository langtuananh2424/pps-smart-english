package com.pps.smartenglish.shared.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** MinIO qua S3 SDK, path-style. Hai endpoint: nội bộ để quản trị bucket, công khai để ký URL. */
@Component
@ConditionalOnProperty(prefix = "app.storage", name = "enabled", havingValue = "true", matchIfMissing = true)
class S3ObjectStorage implements ObjectStorage, ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(S3ObjectStorage.class);
    private static final int BUCKET_ATTEMPTS = 10;

    private final S3Client client;
    private final S3Presigner presigner;
    private static final String REGION = "us-east-1";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final String bucket;
    private final String publicUrl;
    private final String accessKey;
    private final String secretKey;

    S3ObjectStorage(@Value("${app.storage.endpoint}") String endpoint,
                    @Value("${app.storage.public-url}") String publicUrl,
                    @Value("${app.storage.access-key}") String accessKey,
                    @Value("${app.storage.secret-key}") String secretKey,
                    @Value("${app.storage.bucket}") String bucket) {
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        var pathStyle = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        this.bucket = bucket;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
        this.accessKey = accessKey;
        this.secretKey = secretKey;
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

    @Override
    public PostForm presignPost(String key, String contentType, long maxBytes, Duration ttl) {
        Instant now = Instant.now();
        String date = AMZ_DATE.format(now);
        String credential = accessKey + "/" + date.substring(0, 8) + "/" + REGION + "/s3/aws4_request";
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("expiration", DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC).format(now.plus(ttl)));
        policy.put("conditions", List.of(Map.of("bucket", bucket), Map.of("key", key), List.of("content-length-range", 1, maxBytes),
                Map.of("Content-Type", contentType), Map.of("x-amz-algorithm", "AWS4-HMAC-SHA256"),
                Map.of("x-amz-credential", credential), Map.of("x-amz-date", date)));
        String encoded;
        try {
            encoded = Base64.getEncoder().encodeToString(MAPPER.writeValueAsBytes(policy));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("key", key);
        fields.put("Content-Type", contentType);
        fields.put("x-amz-algorithm", "AWS4-HMAC-SHA256");
        fields.put("x-amz-credential", credential);
        fields.put("x-amz-date", date);
        fields.put("policy", encoded);
        fields.put("x-amz-signature", PostPolicySigner.signature(secretKey, date.substring(0, 8), REGION, "s3", encoded));
        return new PostForm(publicUrl + "/" + bucket, fields);
    }

    @Override
    public Optional<ObjectMeta> head(String key) {
        try {
            var h = client.headObject(r -> r.bucket(bucket).key(key));
            return Optional.of(new ObjectMeta(h.contentLength(), h.contentType()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (software.amazon.awssdk.services.s3.model.S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public byte[] readHead(String key, int maxBytes) {
        try (InputStream in = client.getObject(r -> r.bucket(bucket).key(key).range("bytes=0-" + (maxBytes - 1)))) {
            return in.readNBytes(maxBytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public InputStream open(String key) {
        return client.getObject(r -> r.bucket(bucket).key(key));
    }

    @Override
    public void delete(String key) {
        client.deleteObject(r -> r.bucket(bucket).key(key));
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

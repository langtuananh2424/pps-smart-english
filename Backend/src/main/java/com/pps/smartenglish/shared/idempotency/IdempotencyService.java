package com.pps.smartenglish.shared.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pps.smartenglish.shared.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Khóa chống lặp dùng chung (system-design 6.1) cho mọi POST có Idempotency-Key.
 * Cùng (user, key, nội dung) trả lại đúng phản hồi cũ; cùng key khác nội dung là 409.
 */
@Service
public class IdempotencyService {

    public static final String HEADER = "Idempotency-Key";
    private static final int MAX_KEY_LENGTH = 100;

    public record Outcome<T>(int status, T body) {}

    private final IdempotencyRepository keys;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;
    private final Clock clock;

    IdempotencyService(IdempotencyRepository keys, TransactionTemplate tx, ObjectMapper mapper, Clock clock) {
        this.keys = keys;
        this.tx = tx;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Chạy action đúng một lần cho mỗi (user, key). Dòng giữ chỗ được chèn trước action trong cùng transaction với nó:
     * request đồng thời bị unique chặn, chờ request đầu xong rồi nhận lại phản hồi đã lưu. action lỗi thì rollback
     * cả dòng giữ chỗ nên client gửi lại được.
     */
    public <T> Outcome<T> execute(long userId, String key, String requestHash, int status, Class<T> type, Supplier<T> action) {
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw ApiException.validation(HEADER, "Bắt buộc, tối đa " + MAX_KEY_LENGTH + " ký tự");
        }
        var existing = keys.find(userId, key);
        if (existing.isPresent()) {
            return replay(existing.get(), requestHash, type);
        }
        try {
            return tx.execute(s -> {
                var record = keys.saveAndFlush(new IdempotencyRecord(userId, key, requestHash, Instant.now(clock)));
                T result = action.get();
                record.complete(status, write(result));
                return new Outcome<>(status, result);
            });
        } catch (DataIntegrityViolationException e) {
            // request đồng thời đã giữ chỗ trước; transaction của ta đã rollback, đọc lại kết quả của nó
            return replay(keys.find(userId, key).orElseThrow(() -> e), requestHash, type);
        }
    }

    public static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> Outcome<T> replay(IdempotencyRecord record, String requestHash, Class<T> type) {
        if (!record.getRequestHash().equals(requestHash)) {
            throw new ApiException(HttpStatus.CONFLICT, "idempotency_key_reused",
                    "Idempotency-Key này đã được dùng cho một yêu cầu khác");
        }
        try {
            return new Outcome<>(record.getResponseStatus(), mapper.readValue(record.getResponseBody(), type));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không đọc lại được phản hồi đã lưu", e);
        }
    }

    private String write(Object body) {
        try {
            return mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}

package com.pps.smartenglish.shared.error;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** Lỗi nghiệp vụ có mã ổn định cho client; GlobalExceptionHandler dựng envelope từ đây. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, String> details;
    private final Long retryAfterSeconds;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of(), null);
    }

    public ApiException(HttpStatus status, String code, String message, Map<String, String> details, Long retryAfterSeconds) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static ApiException unauthenticated() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "unauthenticated", "Thông tin đăng nhập không hợp lệ");
    }

    public static ApiException rateLimited(long retryAfterSeconds) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", "Quá nhiều yêu cầu, vui lòng thử lại sau",
                Map.of(), Math.max(1, retryAfterSeconds));
    }

    public static ApiException validation(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "validation_failed", "Dữ liệu không hợp lệ", Map.of(field, message), null);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Map<String, String> getDetails() {
        return details;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}

package com.pps.smartenglish.shared.error;

import java.util.Map;
import org.slf4j.MDC;

/** Envelope lỗi chung: { "error": { code, message, requestId, retryable, details } }. */
public record ApiError(Body error) {

    public record Body(String code, String message, String requestId, boolean retryable, Map<String, ?> details) {}

    public static ApiError of(String code, String message, boolean retryable, Map<String, ?> details) {
        return new ApiError(new Body(code, message, MDC.get("requestId"), retryable, details));
    }
}

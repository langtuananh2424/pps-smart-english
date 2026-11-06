package com.pps.smartenglish.ai.provider;

/**
 * Lỗi gọi AI đã phân loại. retryable: lỗi tạm thời (429, 5xx, timeout, kết nối, output sai định dạng) thì thử lại;
 * vĩnh viễn (4xx khác, refusal, max_tokens) thì FAILED. usage có khi nhà cung cấp đã trả phản hồi (vẫn tính token).
 */
public class AiCallException extends Exception {

    private final boolean retryable;
    private final transient AiUsage usage;

    public AiCallException(boolean retryable, String message, AiUsage usage, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
        this.usage = usage;
    }

    public boolean isRetryable() { return retryable; }

    /** Có thể null. */
    public AiUsage usage() { return usage; }
}

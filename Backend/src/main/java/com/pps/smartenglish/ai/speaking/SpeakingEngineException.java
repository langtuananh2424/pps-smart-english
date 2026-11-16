package com.pps.smartenglish.ai.speaking;

/** Lỗi gọi engine đã phân loại: retryable với timeout, kết nối, 429, 5xx; không retry với 4xx khác. */
public class SpeakingEngineException extends Exception {

    private final boolean retryable;

    public SpeakingEngineException(boolean retryable, String message, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() { return retryable; }
}

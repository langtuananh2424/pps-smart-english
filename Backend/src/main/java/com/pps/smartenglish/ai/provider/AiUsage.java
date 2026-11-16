package com.pps.smartenglish.ai.provider;

/** Token đã dùng (FR-AI-04); inputTokens không gồm phần đọc từ cache. audioSeconds chỉ có ở chấm nói. */
public record AiUsage(String provider, String model, int inputTokens, int outputTokens, int cacheReadTokens,
                      int cacheWriteTokens, Double audioSeconds) {

    public AiUsage(String provider, String model, int inputTokens, int outputTokens, int cacheReadTokens, int cacheWriteTokens) {
        this(provider, model, inputTokens, outputTokens, cacheReadTokens, cacheWriteTokens, null);
    }

    public AiUsage withAudio(double seconds) {
        return new AiUsage(provider, model, inputTokens, outputTokens, cacheReadTokens, cacheWriteTokens, seconds);
    }
}

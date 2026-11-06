package com.pps.smartenglish.ai.provider;

/** Token đã dùng (FR-AI-04); inputTokens không gồm phần đọc từ cache. */
public record AiUsage(String provider, String model, int inputTokens, int outputTokens, int cacheReadTokens,
                      int cacheWriteTokens) {}

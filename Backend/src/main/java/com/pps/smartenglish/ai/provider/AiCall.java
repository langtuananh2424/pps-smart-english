package com.pps.smartenglish.ai.provider;

/** Một lời gọi LLM: system prompt cố định (được cache), user prompt, kiểu kết quả có cấu trúc, trần token đầu ra. */
public record AiCall<T>(String systemPrompt, String userPrompt, Class<T> resultType, int maxTokens) {}

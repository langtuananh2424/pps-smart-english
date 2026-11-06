package com.pps.smartenglish.ai.provider;

public record AiResult<T>(T data, AiUsage usage) {}

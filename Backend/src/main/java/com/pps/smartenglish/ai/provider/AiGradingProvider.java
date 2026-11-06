package com.pps.smartenglish.ai.provider;

/** Cổng gọi LLM chấm bài; thay nhà cung cấp chỉ cần thêm một bean triển khai interface này. */
public interface AiGradingProvider {

    /**
     * Gọi một lần, không tự thử lại (worker lo việc thử lại có giãn cách).
     * Lỗi luôn là {@link AiCallException} đã phân loại tạm thời hoặc vĩnh viễn.
     */
    <T> AiResult<T> complete(AiCall<T> call) throws AiCallException;
}

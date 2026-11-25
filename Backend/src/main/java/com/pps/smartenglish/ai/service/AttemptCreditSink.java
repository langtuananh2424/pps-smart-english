package com.pps.smartenglish.ai.service;

import java.math.BigDecimal;

/** D-145: nhận điểm câu Viết/Nói do AI chấm về sau lúc lượt đã nộp; exercise cài đặt, gọi trong transaction hoàn tất job. */
public interface AttemptCreditSink {

    /** credit 0..1. Idempotent: câu đã có điểm (kết quả đến trùng) thì bỏ qua, không cộng lần hai. */
    void applyCredit(long attemptId, long questionId, BigDecimal credit);

    /** D-147: câu không chấm được (đã chấm lại một lần, đã hoàn lượt AI): credit 0 kèm lý do; refunded = lượt AI của câu đã được hoàn. Idempotent như applyCredit. */
    void applyFailure(long attemptId, long questionId, String reason, boolean refunded);
}

package com.pps.smartenglish.commerce.payment;

/** Webhook sai chữ ký (hoặc thiếu trường để kiểm chữ ký). */
public class InvalidWebhookSignatureException extends RuntimeException {

    public InvalidWebhookSignatureException() {
        super("Chữ ký webhook không hợp lệ");
    }
}

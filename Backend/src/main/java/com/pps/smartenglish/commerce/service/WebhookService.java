package com.pps.smartenglish.commerce.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.commerce.payment.InvalidWebhookSignatureException;
import com.pps.smartenglish.commerce.payment.PaymentGatewayRegistry;
import com.pps.smartenglish.shared.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** FR-COM-02: điểm vào duy nhất của webhook, cũng được endpoint giả lập dev gọi để đi đúng luồng thật. */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final PaymentGatewayRegistry gateways;
    private final PaymentProcessor processor;

    WebhookService(PaymentGatewayRegistry gateways, PaymentProcessor processor) {
        this.gateways = gateways;
        this.processor = processor;
    }

    /** Cố ý không @Transactional: transaction nằm trong PaymentProcessor để bắt được lỗi trùng sau khi nó đã rollback. */
    public ProcessOutcome handle(String provider, JsonNode body) {
        var gateway = gateways.find(provider)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "provider_not_found", "Không biết cổng thanh toán"));
        try {
            var paid = gateway.parseWebhook(body);
            if (paid.isEmpty()) {
                return ProcessOutcome.IGNORED;
            }
            return processor.process(gateway.providerCode(), paid.get(), body);
        } catch (InvalidWebhookSignatureException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_signature", "Chữ ký webhook không hợp lệ");
        } catch (DataIntegrityViolationException e) {
            // hai webhook trùng provider_ref chạy đồng thời: bên thua bị unique chặn và đã rollback, không làm lại
            log.info("Webhook trùng provider_ref ({}), bỏ qua", e.getMostSpecificCause().getMessage());
            return ProcessOutcome.DUPLICATE;
        }
    }
}

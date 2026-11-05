package com.pps.smartenglish.commerce.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Cổng giả để dev/test luồng mua, chỉ tồn tại khi app.payment.fake-enabled=true. Webhook ký HMAC-SHA256 (hex) trên
 * "orderCode|amount|reference|status" bằng PAYMENT_FAKE_SECRET.
 */
@Component
@ConditionalOnProperty(prefix = "app.payment", name = "fake-enabled", havingValue = "true")
public class FakePaymentGateway implements PaymentGateway {

    public static final String PROVIDER = "fake";
    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;

    FakePaymentGateway(@Value("${app.payment.fake-secret:}") String secret) {
        if (secret.isBlank()) {
            throw new IllegalStateException("PAYMENT_FAKE_SECRET là bắt buộc khi bật cổng thanh toán giả");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String providerCode() {
        return PROVIDER;
    }

    @Override
    public Link createPaymentLink(LinkRequest r) {
        return new Link("FAKE-" + r.orderCode(), "FAKEQR|" + r.orderCode() + "|" + r.amountVnd());
    }

    @Override
    public Optional<PaidEvent> parseWebhook(JsonNode body) {
        String orderCode = text(body, "orderCode");
        String reference = text(body, "reference");
        String status = text(body, "status");
        String signature = text(body, "signature");
        JsonNode amount = body.path("amount");
        if (orderCode == null || reference == null || status == null || signature == null || !amount.isIntegralNumber()) {
            throw new InvalidWebhookSignatureException();
        }
        byte[] expected = sign(orderCode, amount.asLong(), reference, status).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidWebhookSignatureException();
        }
        return "PAID".equals(status) ? Optional.of(new PaidEvent(orderCode, amount.asLong(), reference)) : Optional.empty();
    }

    /** Payload "đã thanh toán" có chữ ký hợp lệ; dùng cho endpoint giả lập dev và test. */
    public ObjectNode signedPaid(String orderCode, long amountVnd, String reference) {
        return signed(orderCode, amountVnd, reference, "PAID");
    }

    public ObjectNode signed(String orderCode, long amountVnd, String reference, String status) {
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        n.put("orderCode", orderCode).put("amount", amountVnd).put("reference", reference).put("status", status)
                .put("signature", sign(orderCode, amountVnd, reference, status))
                .put("sentAt", Instant.now().toString());
        return n;
    }

    private String sign(String orderCode, long amount, String reference, String status) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return HexFormat.of().formatHex(
                    mac.doFinal((orderCode + "|" + amount + "|" + reference + "|" + status).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String text(JsonNode body, String field) {
        JsonNode n = body.path(field);
        return n.isTextual() && !n.asText().isBlank() ? n.asText() : null;
    }
}

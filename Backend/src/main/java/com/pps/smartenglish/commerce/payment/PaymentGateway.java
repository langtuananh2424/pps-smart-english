package com.pps.smartenglish.commerce.payment;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Optional;

/**
 * Cổng thanh toán QR của bên thứ ba (mẫu theo pps-education). Thêm nhà cung cấp = thêm một @Component
 * triển khai interface này; webhook định tuyến theo providerCode trên đường dẫn, nên giao dịch đang bay
 * của nhà cung cấp cũ vẫn được ghi nhận sau khi đổi.
 */
public interface PaymentGateway {

    /** orderCode duy nhất theo nhà cung cấp; số tiền là đồng. */
    record LinkRequest(String orderCode, long amountVnd, String description, Instant expiresAt) {}

    /** qrContent là chuỗi để client tự vẽ QR. */
    record Link(String paymentLinkId, String qrContent) {}

    /** Giao dịch thành công do webhook báo về; reference là mã giao dịch phía cổng, duy nhất theo nhà cung cấp. */
    record PaidEvent(String orderCode, long amountVnd, String reference) {}

    /** Mã lưu ở payment_transactions.provider và dùng trong đường dẫn webhook (không phân biệt hoa thường). */
    String providerCode();

    Link createPaymentLink(LinkRequest request);

    /**
     * Xác thực và đọc webhook. Sai chữ ký thì ném {@link InvalidWebhookSignatureException}.
     * Trả empty khi payload hợp lệ nhưng không phải giao dịch thành công cần xử lý.
     */
    Optional<PaidEvent> parseWebhook(JsonNode body);
}

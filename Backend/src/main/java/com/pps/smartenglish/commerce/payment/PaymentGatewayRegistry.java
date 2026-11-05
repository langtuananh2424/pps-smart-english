package com.pps.smartenglish.commerce.payment;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class PaymentGatewayRegistry {

    private final List<PaymentGateway> gateways;

    PaymentGatewayRegistry(ObjectProvider<PaymentGateway> gateways) {
        this.gateways = gateways.orderedStream().toList();
    }

    // shortcut: dùng cổng đầu tiên đang bật; khi có cổng thật thêm app.payment.provider để chọn cổng sinh link mới
    public Optional<PaymentGateway> active() {
        return gateways.stream().findFirst();
    }

    public Optional<PaymentGateway> find(String providerCode) {
        return gateways.stream().filter(g -> g.providerCode().equalsIgnoreCase(providerCode)).findFirst();
    }
}

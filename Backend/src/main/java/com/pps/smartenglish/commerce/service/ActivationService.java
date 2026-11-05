package com.pps.smartenglish.commerce.service;

import com.pps.smartenglish.commerce.domain.OrderItem;
import com.pps.smartenglish.commerce.repository.OrderItemRepository;
import com.pps.smartenglish.commerce.repository.ProductRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/** FR-COM-03: cấp quyền theo các sản phẩm của đơn. Phải gọi trong transaction của PaymentProcessor. */
@Component
class ActivationService {

    private final OrderItemRepository items;
    private final ProductRepository products;
    private final EntitlementGranter granter;

    ActivationService(OrderItemRepository items, ProductRepository products, EntitlementGranter granter) {
        this.items = items;
        this.products = products;
        this.granter = granter;
    }

    /** Trả tổng lượt AI của các sản phẩm trong đơn. */
    int activate(long orderId, long userId, Instant now) {
        List<Long> productIds = items.findByOrderId(orderId).stream().map(OrderItem::getProductId).toList();
        return granter.grant(userId, orderId, products.findWithLevels(productIds), now).aiQuota();
    }
}

package com.pps.smartenglish.commerce.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** Giá lúc mua: đổi giá sản phẩm sau đó không ảnh hưởng đơn cũ. */
    @Column(name = "price_vnd", nullable = false)
    private long priceVnd;

    protected OrderItem() {}

    public OrderItem(Long orderId, Long productId, long priceVnd) {
        this.orderId = orderId;
        this.productId = productId;
        this.priceVnd = priceVnd;
    }

    public Long getProductId() { return productId; }
    public long getPriceVnd() { return priceVnd; }
}

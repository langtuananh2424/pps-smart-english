package com.pps.smartenglish.commerce.domain;

import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Bảng orders. Chuyển trạng thái chỉ đi bằng UPDATE nguyên tử trong OrderRepository, không sửa entity. */
@Entity
@Table(name = "orders")
public class ShopOrder extends Auditable {

    public static final String PENDING = "PENDING";
    public static final String PAID = "PAID";
    public static final String EXPIRED = "EXPIRED";
    public static final String CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, updatable = false)
    private String code;

    @Column(nullable = false)
    private String status;

    @Column(name = "total_vnd", nullable = false, updatable = false)
    private long totalVnd;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    private String provider;

    @Column(name = "qr_content")
    private String qrContent;

    protected ShopOrder() {}

    public ShopOrder(Long userId, String code, long totalVnd, Instant expiresAt) {
        this.userId = userId;
        this.code = code;
        this.status = PENDING;
        this.totalVnd = totalVnd;
        this.expiresAt = expiresAt;
    }

    /** Gọi trong cùng transaction tạo đơn, ngay sau khi cổng trả link. */
    public void attachPayment(String provider, String qrContent) {
        this.provider = provider;
        this.qrContent = qrContent;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getCode() { return code; }
    public String getStatus() { return status; }
    public long getTotalVnd() { return totalVnd; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getQrContent() { return qrContent; }
}

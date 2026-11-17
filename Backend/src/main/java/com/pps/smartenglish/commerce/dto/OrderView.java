package com.pps.smartenglish.commerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pps.smartenglish.commerce.dto.OrderCreated.PaymentInfo;
import java.time.Instant;
import java.util.List;

/**
 * GET /orders/{code}; payment và transferContent chỉ có khi đơn còn PENDING (còn hạn), paidAt chỉ có khi đã PAID.
 * status là trạng thái hiệu lực: PENDING quá hạn trả EXPIRED.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderView(String code, String status, long totalVnd, Instant paidAt, Instant expiresAt,
                        List<ItemView> items, PaymentInfo payment, String transferContent) {

    /** course null khi sản phẩm không gắn khóa nào (gói lượt AI). */
    public record ItemView(Long productId, String name, long priceVnd, CourseRef course) {

        /** Dùng cho truy vấn JPQL; course được gắn sau. */
        public ItemView(Long productId, String name, long priceVnd) {
            this(productId, name, priceVnd, null);
        }

        public ItemView withCourse(CourseRef course) {
            return new ItemView(productId, name, priceVnd, course);
        }
    }

    public record CourseRef(Long id, String slug, String title) {}
}

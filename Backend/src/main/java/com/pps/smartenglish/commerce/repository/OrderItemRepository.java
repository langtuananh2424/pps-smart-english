package com.pps.smartenglish.commerce.repository;

import com.pps.smartenglish.commerce.domain.OrderItem;
import com.pps.smartenglish.commerce.dto.OrderView.ItemView;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderId(Long orderId);

    @Query("select new com.pps.smartenglish.commerce.dto.OrderView$ItemView(p.id, p.name, i.priceVnd) "
            + "from OrderItem i, Product p where i.orderId = :orderId and p.id = i.productId order by i.id")
    List<ItemView> findViews(@Param("orderId") Long orderId);
}

package com.pps.smartenglish.commerce.repository;

import com.pps.smartenglish.commerce.domain.OrderItem;
import com.pps.smartenglish.commerce.dto.OrderView.ItemView;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderId(Long orderId);

    /** [orderId, productId, name] của nhiều đơn trong một truy vấn. */
    @Query("select i.orderId, p.id, p.name from OrderItem i, Product p where i.orderId in :orderIds and p.id = i.productId order by i.id")
    List<Object[]> findNames(@Param("orderIds") Collection<Long> orderIds);

    /** [productId, courseId, slug, title]; sản phẩm nhiều level (combo) có nhiều dòng, dòng đầu theo level là khóa chính. */
    @Query("select p.id, c.id, c.slug, c.title from Product p join p.levelIds lid "
            + "join CourseLevel cl on cl.id = lid join Course c on c.id = cl.courseId where p.id in :productIds order by p.id, lid")
    List<Object[]> findCourses(@Param("productIds") Collection<Long> productIds);

    @Query("select new com.pps.smartenglish.commerce.dto.OrderView$ItemView(p.id, p.name, i.priceVnd) "
            + "from OrderItem i, Product p where i.orderId = :orderId and p.id = i.productId order by i.id")
    List<ItemView> findViews(@Param("orderId") Long orderId);
}

package com.pps.smartenglish.commerce.repository;

import com.pps.smartenglish.commerce.domain.Product;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** Một truy vấn lấy cả levelIds (join fetch), không N+1. */
    @Query("select distinct p from Product p left join fetch p.levelIds where p.status = 'ACTIVE' order by p.id")
    List<Product> findActive();

    /** Gói khóa học đang bán (không gồm gói lượt AI), kèm levelIds. */
    @Query("select distinct p from Product p left join fetch p.levelIds where p.status = 'ACTIVE' and p.type = 'LEVEL' order by p.id")
    List<Product> findActiveLevelProducts();

    /** Danh sách quản trị: cả gói đã tắt. */
    @Query("select distinct p from Product p left join fetch p.levelIds order by p.id")
    List<Product> findAllWithLevels();

    @Query("select distinct p from Product p left join fetch p.levelIds where p.id in :ids")
    List<Product> findWithLevels(@Param("ids") Collection<Long> ids);
}

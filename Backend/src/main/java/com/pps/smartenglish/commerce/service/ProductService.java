package com.pps.smartenglish.commerce.service;

import com.pps.smartenglish.commerce.dto.ProductView;
import com.pps.smartenglish.commerce.repository.ProductRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-COM-01: danh sách sản phẩm đang bán. */
@Service
public class ProductService {

    private final ProductRepository products;

    ProductService(ProductRepository products) {
        this.products = products;
    }

    @Transactional(readOnly = true)
    public List<ProductView> listActive() {
        return products.findActive().stream()
                .map(p -> new ProductView(p.getId(), p.getType(), p.getName(), p.getPriceVnd(), p.getAccessDays(),
                        p.getAiQuota(), List.copyOf(p.getLevelIds())))
                .toList();
    }
}

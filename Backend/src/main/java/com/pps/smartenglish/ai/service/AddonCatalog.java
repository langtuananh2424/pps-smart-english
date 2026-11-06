package com.pps.smartenglish.ai.service;

import com.pps.smartenglish.commerce.dto.ProductView;
import com.pps.smartenglish.commerce.service.ProductService;
import java.util.List;
import org.springframework.stereotype.Component;

/** Các gói nạp lượt (AI_ADDON) để gợi ý khi hết lượt (402 quota_exhausted). */
@Component
public class AddonCatalog {

    private final ProductService products;

    AddonCatalog(ProductService products) {
        this.products = products;
    }

    public List<ProductView> addons() {
        return products.listActive().stream().filter(p -> "AI_ADDON".equals(p.type())).toList();
    }
}

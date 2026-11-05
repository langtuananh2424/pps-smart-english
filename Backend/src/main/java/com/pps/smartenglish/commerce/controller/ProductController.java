package com.pps.smartenglish.commerce.controller;

import com.pps.smartenglish.commerce.dto.ProductView;
import com.pps.smartenglish.commerce.service.ProductService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** FR-COM-01: danh sách sản phẩm công khai. */
@RestController
@RequestMapping("/api/products")
class ProductController {

    private final ProductService service;

    ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    List<ProductView> list() {
        return service.listActive();
    }
}

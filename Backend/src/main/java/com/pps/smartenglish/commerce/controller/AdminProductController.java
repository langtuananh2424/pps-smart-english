package com.pps.smartenglish.commerce.controller;

import com.pps.smartenglish.commerce.dto.ProductAdminDtos.Create;
import com.pps.smartenglish.commerce.dto.ProductAdminDtos.Patch;
import com.pps.smartenglish.commerce.dto.ProductAdminDtos.View;
import com.pps.smartenglish.commerce.service.ProductAdminService;
import com.pps.smartenglish.shared.web.PathId;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** FR-COM-07: quyền product.manage do SecurityConfig kiểm. */
@RestController
@RequestMapping("/api/admin/products")
class AdminProductController {

    private final ProductAdminService service;

    AdminProductController(ProductAdminService service) {
        this.service = service;
    }

    @GetMapping
    List<View> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    View create(@Valid @RequestBody Create req) {
        return service.create(req);
    }

    @PatchMapping("/{id}")
    View patch(@PathVariable String id, @Valid @RequestBody Patch req) {
        return service.patch(PathId.parse(id, "product_not_found", "Không tìm thấy gói bán"), req);
    }
}

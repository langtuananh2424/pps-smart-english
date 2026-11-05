package com.pps.smartenglish.commerce.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateOrderRequest(
        @NotEmpty(message = "Chọn ít nhất một sản phẩm") @Size(max = 20, message = "Tối đa 20 sản phẩm")
        List<@NotNull(message = "Không được null") Long> productIds) {}

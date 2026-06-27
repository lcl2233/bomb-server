package com.bomb.module.order.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderCreateRequest {

    @NotNull(message = "productId is required")
    private Long productId;
}

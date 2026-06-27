package com.bomb.module.product.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProductStatusRequest {

    @NotNull(message = "status is required")
    private Integer status;
}

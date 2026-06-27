package com.bomb.module.product.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ProductVO {

    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer durationDays;
    private Integer status;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}

package com.bomb.module.order.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class OrderVO {

    private Long id;
    private String orderNo;
    private Long userId;
    private Long productId;
    private String productName;
    private BigDecimal amount;
    private String status;
    private String payType;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}

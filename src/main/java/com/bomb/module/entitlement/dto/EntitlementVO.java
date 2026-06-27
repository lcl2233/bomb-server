package com.bomb.module.entitlement.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class EntitlementVO {

    private Long id;
    private Long userId;
    private Long productId;
    private Long orderId;
    private String productName;
    private LocalDateTime startAt;
    private LocalDateTime expireAt;
    private String status;
    private Long remainingDays;
}

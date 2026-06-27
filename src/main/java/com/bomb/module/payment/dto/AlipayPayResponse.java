package com.bomb.module.payment.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AlipayPayResponse {

    private String payForm;
    private String orderNo;
}

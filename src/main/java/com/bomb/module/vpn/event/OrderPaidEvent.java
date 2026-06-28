package com.bomb.module.vpn.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class OrderPaidEvent extends ApplicationEvent {

    private final Long orderId;
    private final Long userId;
    private final String orderNo;

    public OrderPaidEvent(Object source, Long orderId, Long userId, String orderNo) {
        super(source);
        this.orderId = orderId;
        this.userId = userId;
        this.orderNo = orderNo;
    }
}

package com.bomb.module.vpn.listener;

import com.bomb.module.order.entity.Order;
import com.bomb.module.order.service.OrderService;
import com.bomb.module.vpn.event.OrderPaidEvent;
import com.bomb.module.vpn.service.VpnProvisioningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaidEventListener {

    private final OrderService orderService;
    private final VpnProvisioningService vpnProvisioningService;

    @Async("vpnTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderPaidEvent event) {
        log.info("start wireguard provisioning for order {}", event.getOrderNo());
        Order order = orderService.getByOrderNo(event.getOrderNo());
        vpnProvisioningService.provisionAfterPayment(order);
    }
}

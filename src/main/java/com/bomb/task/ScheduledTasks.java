package com.bomb.task;

import com.bomb.module.entitlement.service.EntitlementService;
import com.bomb.module.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledTasks {

    private final OrderService orderService;
    private final EntitlementService entitlementService;

    @Scheduled(cron = "0 */5 * * * ?")
    public void cancelExpiredOrders() {
        int count = orderService.cancelExpiredPendingOrders();
        if (count > 0) {
            log.info("cancelled expired pending orders: {}", count);
        }
    }

    @Scheduled(cron = "0 */10 * * * ?")
    public void expireEntitlements() {
        int count = entitlementService.expireOutdatedEntitlements();
        if (count > 0) {
            log.info("expired entitlements: {}", count);
        }
    }
}

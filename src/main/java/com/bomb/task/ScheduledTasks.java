package com.bomb.task;

import com.bomb.module.order.service.OrderService;
import com.bomb.module.payment.service.AlipayService;
import com.bomb.module.vpn.service.VpnProvisioningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 系统定时任务汇总。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledTasks {

    private final OrderService orderService;
    private final AlipayService alipayService;
    private final VpnProvisioningService vpnProvisioningService;

    /**
     * 支付宝待支付订单状态补偿。
     */
    @Scheduled(cron = "0 */2 * * * ?")
    public void syncPendingPayments() {
        int count = alipayService.syncPendingPayments();
        if (count > 0) {
            log.info("synced pending payments: {}", count);
        }
    }

    /**
     * VPN 开通失败自动重试。
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void retryFailedVpnProvisioning() {
        int count = vpnProvisioningService.retryFailedProvisioning();
        if (count > 0) {
            log.info("retried failed vpn provisioning: {}", count);
        }
    }

    /**
     * 取消超时未支付订单。
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void cancelExpiredOrders() {
        int count = orderService.cancelExpiredPendingOrders();
        if (count > 0) {
            log.info("cancelled expired pending orders: {}", count);
        }
    }

    /**
     * 会员权益过期标记和 VPN 回收合并任务。
     * 先把已过期的权益改为 EXPIRED，再回收没有有效权益的 VPN 账号。
     */
    @Scheduled(cron = "0 */10 * * * ?")
    public void expireAndReclaimEntitlements() {
        vpnProvisioningService.expireAndReclaimEntitlements();
    }
}

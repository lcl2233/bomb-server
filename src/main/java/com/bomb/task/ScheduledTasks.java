package com.bomb.task;

import com.bomb.module.entitlement.service.EntitlementService;
import com.bomb.module.order.service.OrderService;
import com.bomb.module.payment.service.AlipayService;
import com.bomb.module.vpn.service.VpnProvisioningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 系统定时任务汇总。
 * <p>
 * cron 格式：秒 分 时 日 月 周（Spring 6 位，比 Linux 多「秒」）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledTasks {

    private final OrderService orderService;
    private final EntitlementService entitlementService;
    private final AlipayService alipayService;
    private final VpnProvisioningService vpnProvisioningService;

    /**
     * 支付宝订单状态补偿同步。
     * <p>
     * 频率：每 2 分钟
     * <p>
     * 背景：用户支付后若异步回调丢失/延迟，订单会一直停在 PENDING。
     * 本任务主动调用支付宝「交易查询」接口，对近 30 分钟内、创建超过 1 分钟的待支付订单
     * 查单；若支付宝侧已是 TRADE_SUCCESS / TRADE_FINISHED，则走 completePaidOrder
     * 完成落库、发权益、触发 VPN 开通。
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
     * <p>
     * 频率：每 5 分钟
     * <p>
     * 扫描 vpn_account.status = FAILED 的记录，按关联订单重新 SSH 执行
     * /etc/wireguard/add-client.sh u{userId}；成功则改 ACTIVE 并写入客户端配置。
     * 典型失败原因：VPS 短暂不可达、脚本执行超时、HostKey 未配置等。
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
     * <p>
     * 频率：每 5 分钟
     * <p>
     * 将创建超过 30 分钟且仍为 PENDING 的订单标记为 CANCELLED，
     * 避免大量僵尸待支付单堆积。
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void cancelExpiredOrders() {
        int count = orderService.cancelExpiredPendingOrders();
        if (count > 0) {
            log.info("cancelled expired pending orders: {}", count);
        }
    }

    /**
     * 过期会员 VPN 下线（从 VPS 删除 WireGuard Peer）。
     * <p>
     * 频率：每小时整点（0 分 0 秒）
     * <p>
     * 流程：
     * 1. 查 vpn_account.status = ACTIVE（VPS 上仍在用的账号）
     * 2. 若用户无有效会员（status=ACTIVE 且 expire_at &gt; 当前时间）→ SSH 执行 remove-client.sh
     * 3. 成功：vpn_account 改 REVOKED、清空 config_content；失败：记录 last_error
     * <p>
     * 权益 status 字段的 EXPIRED 标记由 expireEntitlements 任务负责，本任务只按 expire_at 判断是否该下线。
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void revokeExpiredVpnAccounts() {
        int count = vpnProvisioningService.revokeExpiredVpnAccounts();
        if (count > 0) {
            log.info("revoked expired vpn accounts: {}", count);
        }
    }

    /**
     * 会员权益过期标记（仅改 DB，不操作 VPS）。
     * <p>
     * 频率：每 10 分钟
     * <p>
     * 将 user_entitlement 中 status=ACTIVE 且 expire_at &lt; 当前时间的记录标为 EXPIRED，
     * 供前端「我的权益」等接口及时展示过期状态。
     * <p>
     * 注意：VPS 删 Peer 由上方 revokeExpiredVpnAccounts（每小时）负责；
     * 该任务在 revoke 前也会再跑一遍 expire，两者互补。
     */
    @Scheduled(cron = "0 */10 * * * ?")
    public void expireEntitlements() {
        int count = entitlementService.expireOutdatedEntitlements();
        if (count > 0) {
            log.info("expired entitlements: {}", count);
        }
    }
}

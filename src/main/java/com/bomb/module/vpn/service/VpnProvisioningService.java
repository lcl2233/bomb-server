package com.bomb.module.vpn.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bomb.common.constant.RedisKeys;
import com.bomb.config.VpnWireGuardProperties;
import com.bomb.module.entitlement.service.EntitlementService;
import com.bomb.module.order.entity.Order;
import com.bomb.module.order.service.OrderService;
import com.bomb.module.vpn.dto.SshCommandResult;
import com.bomb.module.vpn.entity.VpnAccount;
import com.bomb.module.vpn.mapper.VpnAccountMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class VpnProvisioningService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_REVOKED = "REVOKED";

    private static final Pattern CONFIG_BLOCK = Pattern.compile(
            "\\[Interface][\\s\\S]*",
            Pattern.MULTILINE
    );

    private final VpnWireGuardProperties properties;
    private final WireGuardSshExecutor sshExecutor;
    private final VpnAccountMapper vpnAccountMapper;
    private final OrderService orderService;
    private final EntitlementService entitlementService;
    private final StringRedisTemplate stringRedisTemplate;

    public VpnAccount getByUserId(Long userId) {
        return vpnAccountMapper.selectOne(new LambdaQueryWrapper<VpnAccount>()
                .eq(VpnAccount::getUserId, userId)
                .last("limit 1"));
    }

    public void provisionAfterPayment(Order order) {
        if (!properties.isEnabled()) {
            log.info("wireguard provisioning disabled, skip order {}", order.getOrderNo());
            return;
        }

        VpnAccount existing = getByUserId(order.getUserId());
        if (existing != null && STATUS_ACTIVE.equals(existing.getStatus())) {
            log.info("wireguard account already exists for user {}, skip order {}",
                    order.getUserId(), order.getOrderNo());
            return;
        }

        String clientName = existing != null && StringUtils.hasText(existing.getClientName())
                ? existing.getClientName()
                : buildClientName(order.getUserId());

        String lockKey = RedisKeys.vpnProvisionLock(order.getId());
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofMinutes(30));
        if (Boolean.FALSE.equals(locked)) {
            log.info("wireguard provisioning already running for order {}", order.getOrderNo());
            return;
        }

        try {
            SshCommandResult result = sshExecutor.runAddClientScript(clientName);
            if (result.getExitCode() != 0) {
                saveFailedAccount(order, clientName, result);
                log.error("wireguard script failed for order {} exit={} stderr={}",
                        order.getOrderNo(), result.getExitCode(), result.getStderr());
                return;
            }

            String configContent = extractClientConfig(result.getStdout());
            if (!StringUtils.hasText(configContent)) {
                saveFailedAccount(order, clientName, result);
                log.error("wireguard config not found in script output for order {}", order.getOrderNo());
                return;
            }

            saveActiveAccount(order, clientName, configContent);
            log.info("wireguard account provisioned for user {} order {} client {}",
                    order.getUserId(), order.getOrderNo(), clientName);
        } catch (Exception ex) {
            saveFailedAccount(order, clientName, ex.getMessage());
            log.error("wireguard provisioning failed for order {}", order.getOrderNo(), ex);
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    public int retryFailedProvisioning() {
        List<VpnAccount> failedAccounts = vpnAccountMapper.selectList(new LambdaQueryWrapper<VpnAccount>()
                .eq(VpnAccount::getStatus, STATUS_FAILED));
        int retried = 0;
        for (VpnAccount account : failedAccounts) {
            try {
                Order order = orderService.getById(account.getOrderId());
                if (order == null) {
                    continue;
                }
                provisionAfterPayment(order);
                VpnAccount latest = getByUserId(account.getUserId());
                if (latest != null && STATUS_ACTIVE.equals(latest.getStatus())) {
                    retried++;
                }
            } catch (Exception ex) {
                log.warn("retry wireguard provisioning failed for user {}", account.getUserId(), ex);
            }
        }
        return retried;
    }

    public int revokeExpiredVpnAccounts() {
        if (!properties.isEnabled()) {
            log.info("wireguard revoke disabled");
            return 0;
        }

        int expired = entitlementService.expireOutdatedEntitlements();
        if (expired > 0) {
            log.info("expired entitlements before vpn revoke: {}", expired);
        }

        List<VpnAccount> activeAccounts = vpnAccountMapper.selectList(new LambdaQueryWrapper<VpnAccount>()
                .eq(VpnAccount::getStatus, STATUS_ACTIVE));
        int revoked = 0;
        for (VpnAccount account : activeAccounts) {
            if (entitlementService.hasActiveEntitlement(account.getUserId())) {
                log.debug("skip vpn revoke for user {}, active entitlement still exists", account.getUserId());
                continue;
            }
            try {
                if (revokeVpnAccount(account)) {
                    revoked++;
                }
            } catch (Exception ex) {
                log.warn("revoke wireguard account failed for user {}", account.getUserId(), ex);
            }
        }
        return revoked;
    }

    private boolean revokeVpnAccount(VpnAccount account) {
        String lockKey = RedisKeys.vpnRevokeLock(account.getUserId());
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofMinutes(30));
        if (Boolean.FALSE.equals(locked)) {
            log.info("wireguard revoke already running for user {}", account.getUserId());
            return false;
        }

        try {
            if (entitlementService.hasActiveEntitlement(account.getUserId())) {
                return false;
            }

            String clientName = account.getClientName();
            if (!StringUtils.hasText(clientName)) {
                clientName = buildClientName(account.getUserId());
            }

            SshCommandResult result = sshExecutor.runRemoveClientScript(clientName);
            if (result.getExitCode() != 0) {
                markRevokeFailed(account, result);
                log.error("wireguard remove script failed for user {} client {} exit={} stderr={}",
                        account.getUserId(), clientName, result.getExitCode(), result.getStderr());
                return false;
            }

            markRevoked(account);
            log.info("wireguard account revoked for user {} client {}", account.getUserId(), clientName);
            return true;
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    @Transactional
    protected void markRevoked(VpnAccount account) {
        account.setStatus(STATUS_REVOKED);
        account.setConfigContent("");
        account.setLastError(null);
        account.setUpdatedAt(LocalDateTime.now());
        vpnAccountMapper.updateById(account);
    }

    @Transactional
    protected void markRevokeFailed(VpnAccount account, SshCommandResult result) {
        account.setLastError(trim("revoke exit=" + result.getExitCode()
                + ", stderr=" + result.getStderr()
                + ", stdout=" + result.getStdout()));
        account.setUpdatedAt(LocalDateTime.now());
        vpnAccountMapper.updateById(account);
    }

    @Transactional
    protected void saveActiveAccount(Order order, String clientName, String configContent) {
        VpnAccount account = getByUserId(order.getUserId());
        if (account == null) {
            account = new VpnAccount();
            account.setUserId(order.getUserId());
            account.setOrderId(order.getId());
            account.setClientName(clientName);
            account.setCreatedAt(LocalDateTime.now());
        }
        account.setConfigContent(configContent);
        account.setStatus(STATUS_ACTIVE);
        account.setLastError(null);
        account.setUpdatedAt(LocalDateTime.now());
        if (account.getId() == null) {
            vpnAccountMapper.insert(account);
        } else {
            vpnAccountMapper.updateById(account);
        }
    }

    @Transactional
    protected void saveFailedAccount(Order order, String clientName, SshCommandResult result) {
        String error = "exit=" + result.getExitCode()
                + ", stderr=" + trim(result.getStderr())
                + ", stdout=" + trim(result.getStdout());
        saveFailedAccount(order, clientName, error);
    }

    @Transactional
    protected void saveFailedAccount(Order order, String clientName, String error) {
        VpnAccount account = getByUserId(order.getUserId());
        if (account == null) {
            account = new VpnAccount();
            account.setUserId(order.getUserId());
            account.setOrderId(order.getId());
            account.setClientName(clientName);
            account.setConfigContent("");
            account.setCreatedAt(LocalDateTime.now());
            vpnAccountMapper.insert(account);
        }
        account.setStatus(STATUS_FAILED);
        account.setLastError(trim(error));
        account.setUpdatedAt(LocalDateTime.now());
        vpnAccountMapper.updateById(account);
    }

    String buildClientName(Long userId) {
        return "u" + userId;
    }

    String extractClientConfig(String stdout) {
        Matcher matcher = CONFIG_BLOCK.matcher(stdout);
        if (matcher.find()) {
            return matcher.group().trim();
        }
        return null;
    }

    private String trim(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > 2000 ? trimmed.substring(0, 2000) : trimmed;
    }
}

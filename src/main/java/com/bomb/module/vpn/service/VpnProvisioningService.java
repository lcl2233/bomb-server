package com.bomb.module.vpn.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bomb.common.constant.RedisKeys;
import com.bomb.config.VpnWireGuardProperties;
import com.bomb.module.order.entity.Order;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class VpnProvisioningService {

    private static final Pattern CONFIG_BLOCK = Pattern.compile(
            "\\[Interface][\\s\\S]*",
            Pattern.MULTILINE
    );

    private final VpnWireGuardProperties properties;
    private final WireGuardSshExecutor sshExecutor;
    private final VpnAccountMapper vpnAccountMapper;
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
        if (existing != null && "ACTIVE".equals(existing.getStatus())) {
            log.info("wireguard account already exists for user {}, skip order {}",
                    order.getUserId(), order.getOrderNo());
            return;
        }

        String lockKey = RedisKeys.vpnProvisionLock(order.getId());
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofMinutes(30));
        if (Boolean.FALSE.equals(locked)) {
            log.info("wireguard provisioning already running for order {}", order.getOrderNo());
            return;
        }

        String clientName = buildClientName(order.getUserId());
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
        }
    }

    @Transactional
    protected void saveActiveAccount(Order order, String clientName, String configContent) {
        VpnAccount account = getByUserId(order.getUserId());
        if (account == null) {
            account = new VpnAccount();
            account.setUserId(order.getUserId());
            account.setOrderId(order.getId());
            account.setClientName(clientName);
            account.setCreatedAt(java.time.LocalDateTime.now());
        }
        account.setConfigContent(configContent);
        account.setStatus("ACTIVE");
        account.setLastError(null);
        account.setUpdatedAt(java.time.LocalDateTime.now());
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
            account.setCreatedAt(java.time.LocalDateTime.now());
            vpnAccountMapper.insert(account);
        }
        account.setStatus("FAILED");
        account.setLastError(trim(error));
        account.setUpdatedAt(java.time.LocalDateTime.now());
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

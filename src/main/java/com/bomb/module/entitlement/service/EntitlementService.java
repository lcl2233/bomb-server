package com.bomb.module.entitlement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bomb.common.constant.EntitlementStatus;
import com.bomb.common.result.PageResult;
import com.bomb.module.entitlement.dto.EntitlementVO;
import com.bomb.module.entitlement.entity.UserEntitlement;
import com.bomb.module.entitlement.mapper.UserEntitlementMapper;
import com.bomb.module.order.entity.Order;
import com.bomb.module.product.entity.Product;
import com.bomb.module.product.service.ProductService;
import com.bomb.module.vpn.entity.VpnAccount;
import com.bomb.module.vpn.service.VpnProvisioningService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EntitlementService {

    private final UserEntitlementMapper userEntitlementMapper;
    private final ProductService productService;
    private final VpnProvisioningService vpnProvisioningService;

    @Transactional
    public UserEntitlement grantEntitlement(Order order) {
        Product product = productService.getById(order.getProductId());
        LocalDateTime now = LocalDateTime.now();
        UserEntitlement active = getActiveEntitlement(order.getUserId());

        UserEntitlement entitlement = new UserEntitlement();
        entitlement.setUserId(order.getUserId());
        entitlement.setProductId(product.getId());
        entitlement.setOrderId(order.getId());
        entitlement.setProductName(product.getName());
        entitlement.setStatus(EntitlementStatus.ACTIVE.name());
        entitlement.setCreatedAt(now);

        if (active != null && active.getExpireAt().isAfter(now)) {
            entitlement.setStartAt(active.getStartAt());
            entitlement.setExpireAt(active.getExpireAt().plusDays(product.getDurationDays()));
            active.setStatus(EntitlementStatus.EXPIRED.name());
            userEntitlementMapper.updateById(active);
        } else {
            entitlement.setStartAt(now);
            entitlement.setExpireAt(now.plusDays(product.getDurationDays()));
        }
        userEntitlementMapper.insert(entitlement);
        return entitlement;
    }

    public EntitlementVO getMyActiveEntitlement(Long userId) {
        UserEntitlement entitlement = getActiveEntitlement(userId);
        if (entitlement == null) {
            return null;
        }
        refreshIfExpired(entitlement);
        if (!EntitlementStatus.ACTIVE.name().equals(entitlement.getStatus())) {
            return null;
        }
        return toVO(entitlement, vpnProvisioningService.getByUserId(userId));
    }

    public PageResult<EntitlementVO> listMyHistory(Long userId, long page, long size) {
        Page<UserEntitlement> entitlementPage = userEntitlementMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<UserEntitlement>()
                        .eq(UserEntitlement::getUserId, userId)
                        .orderByDesc(UserEntitlement::getId)
        );
        List<EntitlementVO> records = entitlementPage.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(entitlementPage.getTotal(), entitlementPage.getCurrent(), entitlementPage.getSize(), records);
    }

    public PageResult<EntitlementVO> listByUserId(Long userId, long page, long size) {
        Page<UserEntitlement> entitlementPage = userEntitlementMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<UserEntitlement>()
                        .eq(userId != null, UserEntitlement::getUserId, userId)
                        .orderByDesc(UserEntitlement::getId)
        );
        List<EntitlementVO> records = entitlementPage.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(entitlementPage.getTotal(), entitlementPage.getCurrent(), entitlementPage.getSize(), records);
    }

    @Transactional
    public int expireOutdatedEntitlements() {
        List<UserEntitlement> entitlements = userEntitlementMapper.selectList(new LambdaQueryWrapper<UserEntitlement>()
                .eq(UserEntitlement::getStatus, EntitlementStatus.ACTIVE.name())
                .lt(UserEntitlement::getExpireAt, LocalDateTime.now()));
        for (UserEntitlement entitlement : entitlements) {
            entitlement.setStatus(EntitlementStatus.EXPIRED.name());
            userEntitlementMapper.updateById(entitlement);
        }
        return entitlements.size();
    }

    public boolean hasActiveEntitlement(Long userId) {
        UserEntitlement entitlement = getActiveEntitlement(userId);
        return entitlement != null && EntitlementStatus.ACTIVE.name().equals(entitlement.getStatus());
    }

    private UserEntitlement getActiveEntitlement(Long userId) {
        UserEntitlement entitlement = userEntitlementMapper.selectOne(new LambdaQueryWrapper<UserEntitlement>()
                .eq(UserEntitlement::getUserId, userId)
                .eq(UserEntitlement::getStatus, EntitlementStatus.ACTIVE.name())
                .orderByDesc(UserEntitlement::getId)
                .last("limit 1"));
        if (entitlement != null) {
            refreshIfExpired(entitlement);
        }
        return entitlement;
    }

    private void refreshIfExpired(UserEntitlement entitlement) {
        if (EntitlementStatus.ACTIVE.name().equals(entitlement.getStatus())
                && entitlement.getExpireAt().isBefore(LocalDateTime.now())) {
            entitlement.setStatus(EntitlementStatus.EXPIRED.name());
            userEntitlementMapper.updateById(entitlement);
        }
    }

    private EntitlementVO toVO(UserEntitlement entitlement) {
        return toVO(entitlement, vpnProvisioningService.getByUserId(entitlement.getUserId()));
    }

    private EntitlementVO toVO(UserEntitlement entitlement, VpnAccount vpnAccount) {
        long remainingDays = 0;
        if (entitlement.getExpireAt() != null && entitlement.getExpireAt().isAfter(LocalDateTime.now())) {
            remainingDays = ChronoUnit.DAYS.between(LocalDateTime.now(), entitlement.getExpireAt());
        }
        return EntitlementVO.builder()
                .id(entitlement.getId())
                .userId(entitlement.getUserId())
                .productId(entitlement.getProductId())
                .orderId(entitlement.getOrderId())
                .productName(entitlement.getProductName())
                .startAt(entitlement.getStartAt())
                .expireAt(entitlement.getExpireAt())
                .status(entitlement.getStatus())
                .remainingDays(remainingDays)
                .vpnClientName(vpnAccount != null && "ACTIVE".equals(vpnAccount.getStatus()) ? vpnAccount.getClientName() : null)
                .vpnConfig(vpnAccount != null && "ACTIVE".equals(vpnAccount.getStatus()) ? vpnAccount.getConfigContent() : null)
                .build();
    }
}

package com.bomb.module.vpn.controller;

import com.bomb.common.result.Result;
import com.bomb.config.DebugProperties;
import com.bomb.module.order.entity.Order;
import com.bomb.module.order.service.OrderService;
import com.bomb.module.vpn.dto.VpnProvisionDebugResponse;
import com.bomb.module.vpn.entity.VpnAccount;
import com.bomb.module.vpn.service.VpnProvisioningService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/debug/vpn")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "bomb.debug", name = "enabled", havingValue = "true")
public class VpnDebugController {

    private final DebugProperties debugProperties;
    private final OrderService orderService;
    private final VpnProvisioningService vpnProvisioningService;

    @PostMapping("/provision/{orderNo}")
    public Result<VpnProvisionDebugResponse> provisionAfterPayment(@PathVariable String orderNo) {
        if (!debugProperties.isEnabled()) {
            return Result.forbidden("debug endpoint disabled");
        }
        Order order = orderService.getByOrderNo(orderNo);
        vpnProvisioningService.provisionAfterPayment(order);
        VpnAccount account = vpnProvisioningService.getByUserId(order.getUserId());
        return Result.success(VpnProvisionDebugResponse.of(order.getOrderNo(), order.getUserId(), account));
    }
}

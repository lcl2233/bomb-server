package com.bomb.module.vpn.dto;

import com.bomb.module.vpn.entity.VpnAccount;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VpnProvisionDebugResponse {

    private String orderNo;
    private Long userId;
    private String clientName;
    private String status;
    private String lastError;
    private boolean hasConfig;

    public static VpnProvisionDebugResponse of(String orderNo, Long userId, VpnAccount account) {
        if (account == null) {
            return VpnProvisionDebugResponse.builder()
                    .orderNo(orderNo)
                    .userId(userId)
                    .build();
        }
        return VpnProvisionDebugResponse.builder()
                .orderNo(orderNo)
                .userId(userId)
                .clientName(account.getClientName())
                .status(account.getStatus())
                .lastError(account.getLastError())
                .hasConfig(account.getConfigContent() != null && !account.getConfigContent().isBlank())
                .build();
    }
}

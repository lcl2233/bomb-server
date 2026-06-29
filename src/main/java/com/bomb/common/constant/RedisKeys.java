package com.bomb.common.constant;

public final class RedisKeys {

    private RedisKeys() {
    }

    public static String payNotifyLock(String alipayTradeNo) {
        return "pay:notify:" + alipayTradeNo;
    }

    public static String payCompleteLock(Long orderId) {
        return "pay:complete:" + orderId;
    }

    public static String vpnProvisionLock(Long orderId) {
        return "vpn:provision:" + orderId;
    }

    public static String vpnRevokeLock(Long userId) {
        return "vpn:revoke:" + userId;
    }
}

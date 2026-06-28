package com.bomb.common.constant;

public final class RedisKeys {

    private RedisKeys() {
    }

    public static String payNotifyLock(String alipayTradeNo) {
        return "pay:notify:" + alipayTradeNo;
    }

    public static String vpnProvisionLock(Long orderId) {
        return "vpn:provision:" + orderId;
    }
}

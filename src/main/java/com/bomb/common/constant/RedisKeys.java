package com.bomb.common.constant;

public final class RedisKeys {

    private RedisKeys() {
    }

    public static String payNotifyLock(String alipayTradeNo) {
        return "pay:notify:" + alipayTradeNo;
    }
}

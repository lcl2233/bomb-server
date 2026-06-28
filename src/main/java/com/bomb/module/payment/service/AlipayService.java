package com.bomb.module.payment.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bomb.common.constant.OrderStatus;
import com.bomb.common.constant.PaymentStatus;
import com.bomb.common.constant.RedisKeys;
import com.bomb.common.exception.BusinessException;
import com.bomb.config.AlipayProperties;
import com.bomb.module.order.entity.Order;
import com.bomb.module.order.service.OrderService;
import com.bomb.module.payment.dto.AlipayPayResponse;
import com.bomb.module.payment.entity.Payment;
import com.bomb.module.payment.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlipayService {

    private static final int PAYMENT_SYNC_WITHIN_MINUTES = 30;
    private static final int PAYMENT_SYNC_MIN_ORDER_AGE_SECONDS = 60;

    private final AlipayClient alipayClient;
    private final AlipayProperties alipayProperties;
    private final OrderService orderService;
    private final PaymentMapper paymentMapper;
    private final PaymentCompletionService paymentCompletionService;
    private final StringRedisTemplate stringRedisTemplate;

    public AlipayPayResponse createPagePay(Long userId, String orderNo) {
        Order order = orderService.getByOrderNo(orderNo);
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(403, "access denied");
        }
        if (!OrderStatus.PENDING.name().equals(order.getStatus())) {
            throw new BusinessException("order is not payable");
        }

        Payment payment = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getOrderId, order.getId())
                .eq(Payment::getStatus, PaymentStatus.PENDING.name())
                .orderByDesc(Payment::getId)
                .last("limit 1"));
        if (payment == null) {
            payment = new Payment();
            payment.setOrderId(order.getId());
            payment.setTradeNo("PT" + IdUtil.getSnowflakeNextIdStr());
            payment.setAmount(order.getAmount());
            payment.setStatus(PaymentStatus.PENDING.name());
            paymentMapper.insert(payment);
        }

        try {
            AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
            request.setNotifyUrl(alipayProperties.getNotifyUrl());
            request.setReturnUrl(alipayProperties.getReturnUrl());
            request.setBizContent(
                    "{\"out_trade_no\":\"%s\",\"product_code\":\"FAST_INSTANT_TRADE_PAY\",\"total_amount\":\"%s\",\"subject\":\"%s\"}"
                            .formatted(
                                    order.getOrderNo(),
                                    order.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                                    order.getProductName()
                            )
            );
            String payForm = alipayClient.pageExecute(request).getBody();
            return AlipayPayResponse.builder()
                    .payForm(payForm)
                    .orderNo(order.getOrderNo())
                    .build();
        } catch (AlipayApiException ex) {
            log.error("create alipay payment failed", ex);
            throw new BusinessException("create alipay payment failed");
        }
    }

    @Transactional
    public String handleNotify(Map<String, String> params) {
        try {
            boolean signVerified = AlipaySignature.rsaCheckV1(
                    params,
                    alipayProperties.getAlipayPublicKey(),
                    "UTF-8",
                    "RSA2"
            );
            if (!signVerified) {
                log.warn("alipay notify sign verify failed");
                return "failure";
            }
        } catch (AlipayApiException ex) {
            log.error("alipay notify verify error", ex);
            return "failure";
        }

        String tradeStatus = params.get("trade_status");
        if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
            return "success";
        }

        String orderNo = params.get("out_trade_no");
        String alipayTradeNo = params.get("trade_no");
        String lockKey = RedisKeys.payNotifyLock(alipayTradeNo);
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofMinutes(10));
        if (Boolean.FALSE.equals(locked)) {
            return "success";
        }

        Order order = orderService.getByOrderNo(orderNo);
        paymentCompletionService.completePaidOrder(order, alipayTradeNo, params.toString());
        return "success";
    }

    public int syncPendingPayments() {
        List<Order> pendingOrders = orderService.listPendingOrdersForPaymentSync(
                PAYMENT_SYNC_WITHIN_MINUTES,
                PAYMENT_SYNC_MIN_ORDER_AGE_SECONDS
        );
        int synced = 0;
        for (Order order : pendingOrders) {
            try {
                if (queryAndCompleteOrder(order)) {
                    synced++;
                }
            } catch (Exception ex) {
                log.warn("sync alipay payment failed for order {}", order.getOrderNo(), ex);
            }
        }
        return synced;
    }

    private boolean queryAndCompleteOrder(Order order) throws AlipayApiException {
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        request.setBizContent("{\"out_trade_no\":\"" + order.getOrderNo() + "\"}");
        AlipayTradeQueryResponse response = alipayClient.execute(request);
        if (!response.isSuccess()) {
            if (!"ACQ.TRADE_NOT_EXIST".equals(response.getSubCode())) {
                log.debug("alipay query pending for order {}: {} {}",
                        order.getOrderNo(), response.getSubCode(), response.getSubMsg());
            }
            return false;
        }

        String tradeStatus = response.getTradeStatus();
        if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
            return false;
        }

        return paymentCompletionService.completePaidOrder(order, response.getTradeNo(), "sync:" + response.getBody());
    }
}

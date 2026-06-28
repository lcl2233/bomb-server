package com.bomb.module.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bomb.common.constant.OrderStatus;
import com.bomb.common.constant.PaymentStatus;
import com.bomb.common.constant.RedisKeys;
import com.bomb.module.entitlement.service.EntitlementService;
import com.bomb.module.order.entity.Order;
import com.bomb.module.order.service.OrderService;
import com.bomb.module.payment.entity.Payment;
import com.bomb.module.payment.mapper.PaymentMapper;
import com.bomb.module.vpn.event.OrderPaidEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCompletionService {

    private final OrderService orderService;
    private final PaymentMapper paymentMapper;
    private final EntitlementService entitlementService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public boolean completePaidOrder(Order order, String alipayTradeNo, String sourceData) {
        if (OrderStatus.PAID.name().equals(order.getStatus())) {
            return false;
        }

        String lockKey = RedisKeys.payCompleteLock(order.getId());
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofMinutes(10));
        if (Boolean.FALSE.equals(locked)) {
            return false;
        }

        Order latestOrder = orderService.getByOrderNo(order.getOrderNo());
        if (OrderStatus.PAID.name().equals(latestOrder.getStatus())) {
            return false;
        }
        if (!OrderStatus.PENDING.name().equals(latestOrder.getStatus())) {
            log.warn("skip completing order {} with status {}", latestOrder.getOrderNo(), latestOrder.getStatus());
            return false;
        }

        orderService.markPaid(latestOrder);

        Payment payment = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getOrderId, latestOrder.getId())
                .orderByDesc(Payment::getId)
                .last("limit 1"));
        if (payment != null) {
            if (StringUtils.hasText(alipayTradeNo)) {
                payment.setAlipayTradeNo(alipayTradeNo);
            }
            payment.setStatus(PaymentStatus.SUCCESS.name());
            payment.setNotifyData(sourceData);
            paymentMapper.updateById(payment);
        }

        entitlementService.grantEntitlement(latestOrder);
        eventPublisher.publishEvent(new  OrderPaidEvent(
                this,
                latestOrder.getId(),
                latestOrder.getUserId(),
                latestOrder.getOrderNo()
        ));
        log.info("order paid completed: {} alipayTradeNo={}", latestOrder.getOrderNo(), alipayTradeNo);
        return true;
    }
}

package com.bomb.module.order.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bomb.common.constant.OrderStatus;
import com.bomb.common.exception.BusinessException;
import com.bomb.common.result.PageResult;
import com.bomb.module.order.dto.OrderCreateRequest;
import com.bomb.module.order.dto.OrderVO;
import com.bomb.module.order.entity.Order;
import com.bomb.module.order.mapper.OrderMapper;
import com.bomb.module.product.entity.Product;
import com.bomb.module.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;
    private final ProductService productService;

    @Transactional
    public OrderVO createOrder(Long userId, OrderCreateRequest request) {
        Product product = productService.getById(request.getProductId());
        if (product.getStatus() != 1) {
            throw new BusinessException("product is not available");
        }
        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setProductId(product.getId());
        order.setProductName(product.getName());
        order.setAmount(product.getPrice());
        order.setStatus(OrderStatus.PENDING.name());
        order.setPayType("ALIPAY");
        orderMapper.insert(order);
        return toVO(order);
    }

    public PageResult<OrderVO> listMyOrders(Long userId, long page, long size) {
        Page<Order> orderPage = orderMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getUserId, userId)
                        .orderByDesc(Order::getId)
        );
        return toPageResult(orderPage);
    }

    public PageResult<OrderVO> listAllOrders(long page, long size) {
        Page<Order> orderPage = orderMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<Order>().orderByDesc(Order::getId)
        );
        return toPageResult(orderPage);
    }

    public OrderVO getMyOrder(Long userId, String orderNo) {
        Order order = getByOrderNo(orderNo);
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(403, "access denied");
        }
        return toVO(order);
    }

    public Order getByOrderNo(String orderNo) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException(404, "order not found");
        }
        return order;
    }

    @Transactional
    public OrderVO cancelOrder(Long userId, String orderNo) {
        Order order = getByOrderNo(orderNo);
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(403, "access denied");
        }
        if (!OrderStatus.PENDING.name().equals(order.getStatus())) {
            throw new BusinessException("only pending orders can be cancelled");
        }
        order.setStatus(OrderStatus.CANCELLED.name());
        orderMapper.updateById(order);
        return toVO(order);
    }

    @Transactional
    public void markPaid(Order order) {
        if (OrderStatus.PAID.name().equals(order.getStatus())) {
            return;
        }
        if (!OrderStatus.PENDING.name().equals(order.getStatus())) {
            throw new BusinessException("order status invalid for payment");
        }
        order.setStatus(OrderStatus.PAID.name());
        order.setPaidAt(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    @Transactional
    public int cancelExpiredPendingOrders() {
        LocalDateTime expireTime = LocalDateTime.now().minusMinutes(30);
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, OrderStatus.PENDING.name())
                .lt(Order::getCreatedAt, expireTime));
        for (Order order : orders) {
            order.setStatus(OrderStatus.CANCELLED.name());
            orderMapper.updateById(order);
        }
        return orders.size();
    }

    private String generateOrderNo() {
        return "BO" + IdUtil.getSnowflakeNextIdStr();
    }

    private PageResult<OrderVO> toPageResult(Page<Order> orderPage) {
        List<OrderVO> records = orderPage.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(orderPage.getTotal(), orderPage.getCurrent(), orderPage.getSize(), records);
    }

    public OrderVO toVO(Order order) {
        return OrderVO.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .userId(order.getUserId())
                .productId(order.getProductId())
                .productName(order.getProductName())
                .amount(order.getAmount())
                .status(order.getStatus())
                .payType(order.getPayType())
                .paidAt(order.getPaidAt())
                .createdAt(order.getCreatedAt())
                .build();
    }
}

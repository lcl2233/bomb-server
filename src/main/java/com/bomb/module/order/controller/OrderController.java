package com.bomb.module.order.controller;

import com.bomb.common.result.PageResult;
import com.bomb.common.result.Result;
import com.bomb.module.order.dto.OrderCreateRequest;
import com.bomb.module.order.dto.OrderVO;
import com.bomb.module.order.service.OrderService;
import com.bomb.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public Result<OrderVO> create(@Valid @RequestBody OrderCreateRequest request) {
        return Result.success(orderService.createOrder(SecurityUtils.getCurrentUserId(), request));
    }

    @GetMapping
    public Result<PageResult<OrderVO>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(orderService.listMyOrders(SecurityUtils.getCurrentUserId(), page, size));
    }

    @GetMapping("/{orderNo}")
    public Result<OrderVO> detail(@PathVariable String orderNo) {
        return Result.success(orderService.getMyOrder(SecurityUtils.getCurrentUserId(), orderNo));
    }

    @PostMapping("/{orderNo}/cancel")
    public Result<OrderVO> cancel(@PathVariable String orderNo) {
        return Result.success(orderService.cancelOrder(SecurityUtils.getCurrentUserId(), orderNo));
    }
}

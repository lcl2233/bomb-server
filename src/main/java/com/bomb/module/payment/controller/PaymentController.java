package com.bomb.module.payment.controller;

import com.bomb.common.result.Result;
import com.bomb.module.payment.dto.AlipayPayResponse;
import com.bomb.module.payment.service.AlipayService;
import com.bomb.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payments/alipay")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final AlipayService alipayService;

    @PostMapping("/{orderNo}")
    public Result<AlipayPayResponse> pay(@PathVariable String orderNo) {
        return Result.success(alipayService.createPagePay(SecurityUtils.getCurrentUserId(), orderNo));
    }

    @PostMapping("/notify")
    public String notify(HttpServletRequest request) {
        Map<String, String> params = extractParams(request);
        log.info("alipay notify received: orderNo={}, tradeNo={}, tradeStatus={}, totalAmount={}",
                params.get("out_trade_no"),
                params.get("trade_no"),
                params.get("trade_status"),
                params.get("total_amount"));
        String result = alipayService.handleNotify(params);
        log.info("alipay notify finished: orderNo={}, tradeNo={}, result={}",
                params.get("out_trade_no"),
                params.get("trade_no"),
                result);
        return result;
    }

    @GetMapping("/return")
    public Result<Map<String, String>> returnPage(HttpServletRequest request) {
        Map<String, String> params = extractParams(request);
        return Result.success(Map.of(
                "orderNo", params.getOrDefault("out_trade_no", ""),
                "tradeNo", params.getOrDefault("trade_no", "")
        ));
    }

    private Map<String, String> extractParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        });
        return params;
    }
}

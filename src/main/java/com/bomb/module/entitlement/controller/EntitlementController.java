package com.bomb.module.entitlement.controller;

import com.bomb.common.result.PageResult;
import com.bomb.common.result.Result;
import com.bomb.module.entitlement.dto.EntitlementVO;
import com.bomb.module.entitlement.service.EntitlementService;
import com.bomb.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/entitlements")
@RequiredArgsConstructor
public class EntitlementController {

    private final EntitlementService entitlementService;

    @GetMapping("/me")
    public Result<EntitlementVO> myEntitlement() {
        return Result.success(entitlementService.getMyActiveEntitlement(SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/history")
    public Result<PageResult<EntitlementVO>> history(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(entitlementService.listMyHistory(SecurityUtils.getCurrentUserId(), page, size));
    }
}

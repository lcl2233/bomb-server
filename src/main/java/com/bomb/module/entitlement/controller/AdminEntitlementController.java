package com.bomb.module.entitlement.controller;

import com.bomb.common.result.PageResult;
import com.bomb.common.result.Result;
import com.bomb.module.entitlement.dto.EntitlementVO;
import com.bomb.module.entitlement.service.EntitlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/entitlements")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminEntitlementController {

    private final EntitlementService entitlementService;

    @GetMapping
    public Result<PageResult<EntitlementVO>> list(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(entitlementService.listByUserId(userId, page, size));
    }
}

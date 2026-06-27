package com.bomb.module.product.controller;

import com.bomb.common.result.PageResult;
import com.bomb.common.result.Result;
import com.bomb.module.product.dto.ProductCreateRequest;
import com.bomb.module.product.dto.ProductStatusRequest;
import com.bomb.module.product.dto.ProductUpdateRequest;
import com.bomb.module.product.dto.ProductVO;
import com.bomb.module.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductController {

    private final ProductService productService;

    @GetMapping
    public Result<PageResult<ProductVO>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(productService.listAll(page, size));
    }

    @GetMapping("/{id}")
    public Result<ProductVO> detail(@PathVariable Long id) {
        return Result.success(productService.getByIdVO(id));
    }

    @PostMapping
    public Result<ProductVO> create(@Valid @RequestBody ProductCreateRequest request) {
        return Result.success(productService.create(request));
    }

    @PutMapping("/{id}")
    public Result<ProductVO> update(@PathVariable Long id, @Valid @RequestBody ProductUpdateRequest request) {
        return Result.success(productService.update(id, request));
    }

    @PutMapping("/{id}/status")
    public Result<ProductVO> updateStatus(@PathVariable Long id, @Valid @RequestBody ProductStatusRequest request) {
        return Result.success(productService.updateStatus(id, request.getStatus()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return Result.success();
    }
}

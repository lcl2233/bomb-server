package com.bomb.module.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bomb.common.exception.BusinessException;
import com.bomb.common.result.PageResult;
import com.bomb.module.product.dto.ProductCreateRequest;
import com.bomb.module.product.dto.ProductUpdateRequest;
import com.bomb.module.product.dto.ProductVO;
import com.bomb.module.product.entity.Product;
import com.bomb.module.product.mapper.ProductMapper;
import com.bomb.security.AdminAuthorization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductMapper productMapper;

    public PageResult<ProductVO> listPublic(long page, long size) {
        Page<Product> productPage = productMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getStatus, 1)
                        .orderByAsc(Product::getSortOrder)
                        .orderByDesc(Product::getId)
        );
        return toPageResult(productPage);
    }

    public PageResult<ProductVO> listAll(long page, long size) {
        AdminAuthorization.requireAdmin();
        Page<Product> productPage = productMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<Product>()
                        .orderByAsc(Product::getSortOrder)
                        .orderByDesc(Product::getId)
        );
        return toPageResult(productPage);
    }

    public ProductVO getPublicById(Long id) {
        Product product = getById(id);
        if (product.getStatus() != 1) {
            throw new BusinessException(404, "product not found");
        }
        return toVO(product);
    }

    public ProductVO getByIdVO(Long id) {
        AdminAuthorization.requireAdmin();
        return toVO(getById(id));
    }

    public Product getById(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(404, "product not found");
        }
        return product;
    }

    public ProductVO create(ProductCreateRequest request) {
        AdminAuthorization.requireAdmin();
        Product product = new Product();
        copyRequest(product, request);
        product.setStatus(1);
        productMapper.insert(product);
        return toVO(product);
    }

    public ProductVO update(Long id, ProductUpdateRequest request) {
        AdminAuthorization.requireAdmin();
        Product product = getById(id);
        copyRequest(product, request);
        productMapper.updateById(product);
        return toVO(product);
    }

    public ProductVO updateStatus(Long id, Integer status) {
        AdminAuthorization.requireAdmin();
        if (status != 0 && status != 1) {
            throw new BusinessException("invalid product status");
        }
        Product product = getById(id);
        product.setStatus(status);
        productMapper.updateById(product);
        return toVO(product);
    }

    public void delete(Long id) {
        AdminAuthorization.requireAdmin();
        Product product = getById(id);
        productMapper.deleteById(product.getId());
    }

    private void copyRequest(Product product, ProductCreateRequest request) {
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setDurationDays(request.getDurationDays());
        product.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
    }

    private PageResult<ProductVO> toPageResult(Page<Product> productPage) {
        List<ProductVO> records = productPage.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(productPage.getTotal(), productPage.getCurrent(), productPage.getSize(), records);
    }

    public ProductVO toVO(Product product) {
        return ProductVO.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .durationDays(product.getDurationDays())
                .status(product.getStatus())
                .sortOrder(product.getSortOrder())
                .createdAt(product.getCreatedAt())
                .build();
    }
}

package com.bomb.module.product.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProductUpdateRequest extends ProductCreateRequest {
}

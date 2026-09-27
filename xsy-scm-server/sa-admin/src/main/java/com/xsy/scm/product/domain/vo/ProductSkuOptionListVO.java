package com.xsy.scm.product.domain.vo;

import java.util.List;

public record ProductSkuOptionListVO(List<
        ProductSkuOptionVO> options, boolean truncated) {
}

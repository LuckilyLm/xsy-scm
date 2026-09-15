package net.lab1024.sa.admin.module.scm.product.domain.vo;

import java.util.List;
public record ProductSkuOptionListVO(List<ProductSkuOptionVO> options, boolean truncated) {}

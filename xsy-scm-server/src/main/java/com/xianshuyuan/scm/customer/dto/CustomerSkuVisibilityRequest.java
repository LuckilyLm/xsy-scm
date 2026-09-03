package com.xianshuyuan.scm.customer.dto;
import jakarta.validation.constraints.NotNull;
public record CustomerSkuVisibilityRequest(Long id,Integer version,@NotNull Long skuId) {}

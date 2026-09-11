package com.xianshuyuan.scm.customer.dto;

public record CustomerTypePricePageQuery(long page, long pageSize, Long customerTypeId, Long skuId, String keyword) {
}

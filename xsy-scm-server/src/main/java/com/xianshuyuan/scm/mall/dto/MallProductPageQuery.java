package com.xianshuyuan.scm.mall.dto;

import com.xianshuyuan.scm.customer.entity.VisibilityPolicy;

public record MallProductPageQuery(long page, long pageSize, String keyword, Long categoryId,
                                   long customerId, VisibilityPolicy visibilityPolicy) {
}

package com.xianshuyuan.scm.mall.vo;

import java.util.List;

public record MallCartResponse(List<MallCartItemResponse> items, String totalQuantity, String totalAmount,
                               int unavailableCount) {
}

package com.xianshuyuan.scm.mall.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 结算预览请求。不携带任何价格，价格一律由服务端解析。
 */
public record MallCheckoutPreviewRequest(
        @NotEmpty(message = "请先选择要结算的商品") List<@Valid MallCheckoutItemRequest> items,
        @NotNull(message = "请选择收货地址") Long addressId
) {
}

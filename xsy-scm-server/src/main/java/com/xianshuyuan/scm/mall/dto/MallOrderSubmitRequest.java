package com.xianshuyuan.scm.mall.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 商城下单请求。不含客户、来源和价格，全部由服务端按登录身份确定。
 */
public record MallOrderSubmitRequest(
        @NotEmpty(message = "请先选择要下单的商品") List<@Valid MallCheckoutItemRequest> items,
        @NotNull(message = "请选择收货地址") Long addressId,
        @NotBlank(message = "请确认商品价格后重新提交") String priceFingerprint
) {
}

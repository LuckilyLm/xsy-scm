package com.xianshuyuan.scm.mall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MallAddressSaveRequest(
        @NotBlank(message = "收货人不能为空") @Size(max = 100, message = "收货人姓名过长") String receiverName,
        @NotBlank(message = "联系电话不能为空") @Size(max = 32, message = "联系电话过长") String phone,
        @NotBlank(message = "所在地区不能为空") @Size(max = 200, message = "所在地区过长") String region,
        @NotBlank(message = "详细地址不能为空") @Size(max = 300, message = "详细地址过长") String detailAddress,
        boolean defaultAddress
) {
}

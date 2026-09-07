package com.xianshuyuan.scm.product.dto;

import com.xianshuyuan.scm.product.entity.ShelfStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ProductStatusRequest(
        @NotNull(message = "版本号不能为空")
        @PositiveOrZero(message = "版本号不正确")
        Integer version,
        @NotNull(message = "商品状态不能为空")
        ShelfStatus status
) {
}

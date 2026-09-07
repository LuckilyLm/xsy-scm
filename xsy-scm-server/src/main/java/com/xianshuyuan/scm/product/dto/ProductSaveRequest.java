package com.xianshuyuan.scm.product.dto;

import com.xianshuyuan.scm.product.entity.ShelfStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProductSaveRequest(
        Integer version,
        @NotBlank(message = "SPU 编码不能为空")
        @Size(max = 64, message = "SPU 编码不能超过64个字符")
        String spuCode,
        @NotBlank(message = "商品名称不能为空")
        @Size(max = 150, message = "商品名称不能超过150个字符")
        String name,
        @Size(max = 150, message = "商品别名不能超过150个字符")
        String alias,
        @NotNull(message = "商品分类不能为空")
        Long categoryId,
        @Size(max = 1000, message = "商品简介不能超过1000个字符")
        String description,
        @NotNull(message = "SPU 状态不能为空")
        ShelfStatus status,
        @NotEmpty(message = "商品至少需要一个 SKU")
        List<@Valid ProductSkuSaveRequest> skus
) {
}
